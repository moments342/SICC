package com.moments.sicc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.service.VigenciaScheduler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-formalizacao;MODE=PostgreSQL")
@AutoConfigureMockMvc
@Import(FormalizacaoInstrumentoApiContractTest.RelogioFixoConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FormalizacaoInstrumentoApiContractTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 8, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProcessoAdministrativoRepository processos;
    @Autowired
    private VigenciaScheduler vigenciaScheduler;

    @Test
    void formalizacaoValidaVinculaPdfMantemTramitacaoEExpoeSomenteAllowlistPublica()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-FORMAL-010");
        long setorId = criarSetor(token, "DIPAC", "Divisao de Parcerias");
        movimentar(token, processoId, setorId, "Analise inicial");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "instrumento.pdf", "%PDF-1.4\n%%EOF");

        MvcResult formalizado = mockMvc.perform(post(
                        "/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formalizacao(documentoId).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("CONVENIO"))
                .andExpect(jsonPath("$.vigenciaContratualFinal").value("2027-08-01"))
                .andExpect(jsonPath("$.vigenciaTedFinal").value("2027-04-30"))
                .andExpect(jsonPath("$.dataFormalizacao").value(HOJE.toString()))
                .andExpect(jsonPath("$.documentoAssinadoId").value(documentoId))
                .andExpect(jsonPath("$.vigenciaContratualInicial").doesNotExist())
                .andExpect(jsonPath("$.vigenciaTedInicial").doesNotExist())
                .andReturn();
        long instrumentoId = json(formalizado).get("id").asLong();

        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_VIGENCIA"))
                .andExpect(jsonPath("$.instrumento.id").value(instrumentoId))
                .andExpect(jsonPath("$.instrumento.documentoAssinadoId").value(documentoId));
        mockMvc.perform(get("/api/v1/processos/{id}/tramitacao", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movimentacoes.length()").value(1))
                .andExpect(jsonPath("$.movimentacoes[0].observacao").value("Analise inicial"));
        mockMvc.perform(get("/api/v1/documentos")
                        .queryParam("proprietarioTipo", "INSTRUMENTO")
                        .queryParam("proprietarioId", Long.toString(instrumentoId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(documentoId))
                .andExpect(jsonPath("$[0].categoria").value("ASSINADO"));

        MvcResult consultaPublica = mockMvc.perform(get("/api/v1/public/processos")
                        .queryParam("numero", "PROC-FORMAL-010"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipoInstrumento").value("CONVENIO"))
                .andExpect(jsonPath("$.content[0].coordenador").value("Maria Silva"))
                .andExpect(jsonPath("$.content[0].status").value("EM_VIGENCIA"))
                .andExpect(jsonPath("$.content[0].vigenciaContratualFinal").value("2027-08-01"))
                .andExpect(jsonPath("$.content[0].vigenciaTedFinal").value("2027-04-30"))
                .andReturn();
        assertThat(json(consultaPublica).get("content").get(0).properties())
                .extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder(
                        "numeroProcesso", "tipoInstrumento", "origem", "coordenador",
                        "status", "vigenciaContratualFinal", "vigenciaTedFinal");

        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "FORMALIZAR_INSTRUMENTO")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].objeto.tipo")
                        .value("INSTRUMENTO_CONTRATUAL"))
                .andExpect(jsonPath("$.content[0].objeto.id").value(instrumentoId));
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "VINCULAR_DOCUMENTO_ASSINADO")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].objeto.tipo").value("DOCUMENTO"))
                .andExpect(jsonPath("$.content[0].objeto.id").value(documentoId));
    }

    @Test
    void tipoTedNaoPertenceAoCatalogoFechado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TED-010");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "instrumento.pdf", "%PDF-1.4\n%%EOF");
        ObjectNode request = formalizacao(documentoId);
        request.put("tipo", "TED");

        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_FORMALIZACAO"))
                .andExpect(jsonPath("$.instrumento").doesNotExist());
    }

    @Test
    void formalizacaoRejeitaParticipeEmBrancoEDataAusente() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-CAMPOS-010");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "instrumento.pdf", "%PDF-1.4\n%%EOF");
        ObjectNode participanteEmBranco = formalizacao(documentoId);
        participanteEmBranco.putArray("participes").add("   ");

        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participanteEmBranco.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem")
                        .value(org.hamcrest.Matchers.containsString("participes")));

        ObjectNode dataAusente = formalizacao(documentoId);
        dataAusente.remove("dataFormalizacao");
        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dataAusente.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem")
                        .value(org.hamcrest.Matchers.containsString("dataFormalizacao")));
    }

    @Test
    void formalizacaoExigeDocumentoAssinadoPdfDoMesmoProcesso() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-DOC-010");
        long documentoAdministrativoId = criarDocumento(
                token, processoId, "ADMINISTRATIVO", "minuta.pdf", "%PDF-1.4\n%%EOF");

        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formalizacao(documentoAdministrativoId).toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("A formalização exige um Documento Assinado ativo."));
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_FORMALIZACAO"))
                .andExpect(jsonPath("$.instrumento").doesNotExist());
        mockMvc.perform(get("/api/v1/documentos")
                        .queryParam("proprietarioTipo", "PROCESSO")
                        .queryParam("proprietarioId", Long.toString(processoId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(documentoAdministrativoId));
    }

    @Test
    void formalizacoesConcorrentesCriamExatamenteUmInstrumentoERejeitamADuplicata()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-CONCORRENTE-010");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "instrumento.pdf", "%PDF-1.4\n%%EOF");
        CountDownLatch inicio = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var resultados = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(indice -> executor.submit(() ->
                            formalizarConcorrentemente(inicio, token, processoId, documentoId)))
                    .toList();
            inicio.countDown();

            assertThat(resultados.stream().map(futuro -> {
                try {
                    return futuro.get();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }).toList()).containsExactlyInAnyOrder(201, 422);
        }

        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_VIGENCIA"))
                .andExpect(jsonPath("$.instrumento.documentoAssinadoId").value(documentoId));
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "FORMALIZAR_INSTRUMENTO")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listasCalculamStatusPelaVigenciaContratualSemSerAfetadasPeloTed() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoContratualVencido = criarProcesso(token, "PROC-VENCIDO-011");
        long documentoContratual = criarDocumento(
                token, processoContratualVencido, "ASSINADO", "contrato.pdf", "%PDF-1.4\n%%EOF");
        ObjectNode contratoVencido = formalizacao(documentoContratual);
        contratoVencido.put("vigenciaContratualFinal", HOJE.minusDays(1).toString());
        contratoVencido.put("vigenciaTedFinal", HOJE.plusDays(365).toString());
        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoContratualVencido)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contratoVencido.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacaoContratual").value("VENCIDA"))
                .andExpect(jsonPath("$.situacaoTed").value("VALIDA"));

        var statusPersistidoDesatualizado = processos.findById(processoContratualVencido).orElseThrow();
        statusPersistidoDesatualizado.setStatus(StatusProcesso.EM_VIGENCIA);
        processos.saveAndFlush(statusPersistidoDesatualizado);

        mockMvc.perform(get("/api/v1/processos")
                        .queryParam("numero", "PROC-VENCIDO-011")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CONCLUIDO"));
        mockMvc.perform(get("/api/v1/public/processos")
                        .queryParam("numero", "PROC-VENCIDO-011"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CONCLUIDO"));

        long processoTedVencido = criarProcesso(token, "PROC-TED-VENCIDO-011");
        long documentoTed = criarDocumento(
                token, processoTedVencido, "ASSINADO", "ted.pdf", "%PDF-1.4\n%%EOF");
        ObjectNode tedVencido = formalizacao(documentoTed);
        tedVencido.put("vigenciaContratualFinal", HOJE.plusDays(365).toString());
        tedVencido.put("vigenciaTedFinal", HOJE.minusDays(1).toString());
        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoTedVencido)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tedVencido.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacaoContratual").value("VALIDA"))
                .andExpect(jsonPath("$.situacaoTed").value("VENCIDA"));
        mockMvc.perform(get("/api/v1/processos/{id}", processoTedVencido)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_VIGENCIA"));
        mockMvc.perform(get("/api/v1/public/processos")
                        .queryParam("numero", "PROC-TED-VENCIDO-011"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("EM_VIGENCIA"));
    }

    @Test
    void processamentoProgramadoPersisteCadaAlertaUmaVezEVinculaOProcesso() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-ALERTA-011");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "alerta.pdf", "%PDF-1.4\n%%EOF");
        ObjectNode request = formalizacao(documentoId);
        request.put("vigenciaContratualFinal", HOJE.plusDays(120).toString());
        request.put("vigenciaTedFinal", HOJE.plusDays(120).toString());
        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isCreated());

        vigenciaScheduler.avaliar();
        vigenciaScheduler.avaliar();

        MvcResult caixaDeEntrada = mockMvc.perform(get("/api/v1/notificacoes")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode alertas = json(caixaDeEntrada);
        assertThat(alertas).hasSize(2);
        assertThat(alertas).allSatisfy(alerta -> {
            assertThat(alerta.get("processoId").asLong()).isEqualTo(processoId);
            assertThat(alerta.get("mensagem").asText()).contains("120 dias");
        });
        assertThat(alertas).extracting(alerta -> alerta.get("tipo").asText())
                .containsExactlyInAnyOrder(
                        "ALERTA_VIGENCIA_CONTRATUAL", "ALERTA_VIGENCIA_TED");
    }

    private long criarProcesso(String token, String numero) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "numero", numero,
                                "origem", "DIPAC"))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private long criarSetor(String token, String sigla, String nome) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/admin/setores")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "sigla", sigla,
                                "nome", nome))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private void movimentar(String token, long processoId, long setorId, String observacao)
            throws Exception {
        mockMvc.perform(post("/api/v1/movimentacoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "contextoTipo", "FORMALIZACAO",
                                "contextoId", processoId,
                                "dataMovimentacao", HOJE.minusDays(3),
                                "setorDestinoId", setorId,
                                "observacao", observacao))))
                .andExpect(status().isCreated());
    }

    private long criarDocumento(
            String token,
            long processoId,
            String categoria,
            String nome,
            String conteudo) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", nome, MediaType.APPLICATION_OCTET_STREAM_VALUE, conteudo.getBytes());
        MvcResult resultado = mockMvc.perform(multipart("/api/v1/documentos")
                        .file(arquivo)
                        .param("proprietarioTipo", "PROCESSO")
                        .param("proprietarioId", Long.toString(processoId))
                        .param("categoria", categoria)
                        .param("titulo", nome)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private ObjectNode formalizacao(long documentoId) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("numero", "CV-010/2026");
        request.put("tipo", "CONVENIO");
        request.put("objeto", "Cooperacao institucional");
        request.put("descricao", "Instrumento formalizado pela DIPAC");
        request.put("natureza", "Administrativa");
        request.put("coordenador", "Maria Silva");
        request.putArray("participes").add("UFGD").add("Fundacao");
        request.put("valorAtual", 150000);
        request.put("vigenciaContratualFinal", "2027-08-01");
        request.put("vigenciaTedFinal", "2027-04-30");
        request.put("dataFormalizacao", HOJE.toString());
        request.put("documentoAssinadoId", documentoId);
        return request;
    }

    private int formalizarConcorrentemente(
            CountDownLatch inicio,
            String token,
            long processoId,
            long documentoId) {
        try {
            inicio.await();
            return mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(formalizacao(documentoId).toString()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String tokenAdministradorPermanente() throws Exception {
        String temporario = tokenDoLogin("admin", "Temporaria123!", true);
        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", bearer(temporario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Temporaria123!","novaSenha":"Permanente123!"}
                                """))
                .andExpect(status().isNoContent());
        return tokenDoLogin("admin", "Permanente123!", false);
    }

    private String tokenDoLogin(String login, String senha, boolean trocaObrigatoria)
            throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","senha":"%s"}
                                """.formatted(login, senha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trocaSenhaObrigatoria").value(trocaObrigatoria))
                .andReturn();
        return json(resultado).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
