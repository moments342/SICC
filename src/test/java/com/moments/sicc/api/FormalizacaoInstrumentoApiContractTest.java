package com.moments.sicc.api;

import static com.moments.sicc.support.ArquivoDocumentoTeste.pdfValido;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.repository.DocumentoRepository;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.service.VigenciaScheduler;
import com.moments.sicc.support.RelogioControlavel;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-formalizacao;MODE=PostgreSQL")
@AutoConfigureMockMvc
@Import(FormalizacaoInstrumentoApiContractTest.RelogioFixoConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FormalizacaoInstrumentoApiContractTest extends ApiContractTestSupport {

    private static final LocalDate HOJE = LocalDate.of(2026, 8, 1);
    private static final String AGORA_JSON = "2026-08-01T12:00:00";

    @Autowired
    private ProcessoAdministrativoRepository processos;
    @MockitoSpyBean
    private DocumentoRepository documentos;
    @Autowired
    private VigenciaScheduler vigenciaScheduler;
    @Autowired
    private RelogioControlavel relogio;

    @Test
    void consultaInternaFiltraPorObjetoECoordenadorSemExporObjetoNaConsultaPublica()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoFormalizado = criarProcesso(token, "PROC-FILTROS-INSTRUMENTO-010");
        criarProcesso(token, "PROC-AINDA-EM-FORMALIZACAO-010");
        long documentoId = criarDocumento(
                token,
                processoFormalizado,
                "ASSINADO",
                "instrumento-filtros.pdf",
                pdfValido("instrumento-filtros"));
        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoFormalizado)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formalizacao(documentoId).toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/processos")
                        .queryParam("objeto", "institucional")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(processoFormalizado));
        mockMvc.perform(get("/api/v1/processos")
                        .queryParam("coordenador", "maria")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(processoFormalizado));
        mockMvc.perform(get("/api/v1/processos")
                        .queryParam("objeto", "institucional")
                        .queryParam("coordenador", "outra pessoa")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/public/processos")
                        .queryParam("objeto", "objeto privado inexistente")
                        .queryParam("coordenador", "coordenador inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].objeto").doesNotExist());
    }

    @Test
    void formalizacaoValidaVinculaPdfMantemTramitacaoEExpoeSomenteAllowlistPublica()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-FORMAL-010");
        long setorId = criarSetor(token, "DIPAC", "Divisao de Parcerias");
        movimentar(token, processoId, setorId, "Analise inicial");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "instrumento.pdf", pdfValido("instrumento"));

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
                token, processoId, "ASSINADO", "instrumento.pdf", pdfValido("tipo-ted"));
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
                token, processoId, "ASSINADO", "instrumento.pdf", pdfValido("campos"));
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
                token, processoId, "ADMINISTRATIVO", "minuta.pdf", pdfValido("minuta"));

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
    void processoInativoNaoPodeSerFormalizado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-INATIVO-FORMAL-018");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "instrumento.pdf", pdfValido("inativo"));

        mockMvc.perform(delete("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formalizacao(documentoId).toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("O Processo Administrativo está inativo."));
    }

    @Test
    void formalizacoesConcorrentesCriamExatamenteUmInstrumentoERejeitamADuplicata()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-CONCORRENTE-010");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "instrumento.pdf", pdfValido("concorrente"));
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
                token, processoContratualVencido, "ASSINADO", "contrato.pdf", pdfValido("contrato"));
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
        mockMvc.perform(get("/api/v1/processos")
                        .queryParam("numero", "PROC-VENCIDO-011")
                        .queryParam("status", "CONCLUIDO")
                        .queryParam("vigencia", "VENCIDA")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CONCLUIDO"));
        mockMvc.perform(get("/api/v1/public/processos")
                        .queryParam("numero", "PROC-VENCIDO-011")
                        .queryParam("status", "EM_VIGENCIA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        long processoTedVencido = criarProcesso(token, "PROC-TED-VENCIDO-011");
        long documentoTed = criarDocumento(
                token, processoTedVencido, "ASSINADO", "ted.pdf", pdfValido("ted"));
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
        mockMvc.perform(get("/api/v1/public/processos")
                        .queryParam("numero", "PROC-TED-VENCIDO-011")
                        .queryParam("status", "EM_VIGENCIA")
                        .queryParam("vigencia", "VENCIDA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("EM_VIGENCIA"));
    }

    @Test
    void consultasMantemFiltroEProjecaoNaMesmaReferenciaTemporalDuranteViradaDoDia()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-VIRADA-DIA-011");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "virada.pdf", pdfValido("virada"));
        ObjectNode request = formalizacao(documentoId);
        request.put("vigenciaContratualFinal", HOJE.toString());
        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isCreated());

        relogio.avancarAposLeituras(2, HOJE.plusDays(1));
        mockMvc.perform(get("/api/v1/processos")
                        .queryParam("numero", "PROC-VIRADA-DIA-011")
                        .queryParam("status", "EM_VIGENCIA")
                        .queryParam("vigencia", "PROXIMA_VENCIMENTO")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("EM_VIGENCIA"))
                .andExpect(jsonPath("$.content[0].instrumento.situacaoContratual")
                        .value("PROXIMA_VENCIMENTO"));

        relogio.redefinir(HOJE);
        relogio.avancarAposLeituras(2, HOJE.plusDays(1));
        mockMvc.perform(get("/api/v1/public/processos")
                        .queryParam("numero", "PROC-VIRADA-DIA-011")
                        .queryParam("status", "EM_VIGENCIA")
                        .queryParam("vigencia", "PROXIMA_VENCIMENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("EM_VIGENCIA"));
    }

    @Test
    void processamentoProgramadoPersisteCadaAlertaUmaVezEVinculaOProcesso() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-ALERTA-011");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "alerta.pdf", pdfValido("alerta"));
        ObjectNode request = formalizacao(documentoId);
        request.put("vigenciaContratualFinal", HOJE.plusDays(120).toString());
        request.put("vigenciaTedFinal", HOJE.plusDays(120).toString());
        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/processos")
                        .queryParam("numero", "PROC-ALERTA-011")
                        .queryParam("vigencia", "PROXIMA_VENCIMENTO")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/public/processos")
                        .queryParam("numero", "PROC-ALERTA-011")
                        .queryParam("vigencia", "PROXIMA_VENCIMENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

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

    @Test
    void formalizacaoFixaVersaoExataEBloqueiaMutacaoDoDocumentoOficial() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-VERSAO-OFICIAL-016");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "instrumento-v1.pdf", pdfValido("versao-1"));
        byte[] versaoOficial = pdfValido("versao-2-oficial");

        MvcResult segundaVersao = mockMvc.perform(multipart(
                        "/api/v1/documentos/{id}/versoes", documentoId)
                        .file(new MockMultipartFile(
                                "arquivo", "instrumento-v2.pdf",
                                MediaType.APPLICATION_PDF_VALUE, versaoOficial))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versoes[0].versao").value(2))
                .andReturn();
        String checksumOficial = json(segundaVersao)
                .path("versoes").path(0).path("checksumSha256").asText();

        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formalizacao(documentoId).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentoAssinadoId").value(documentoId))
                .andExpect(jsonPath("$.documentoAssinadoVersao").value(2))
                .andExpect(jsonPath("$.documentoAssinadoChecksumSha256").value(checksumOficial));

        mockMvc.perform(multipart("/api/v1/documentos/{id}/versoes", documentoId)
                        .file(new MockMultipartFile(
                                "arquivo", "instrumento-v3.pdf",
                                MediaType.APPLICATION_PDF_VALUE, pdfValido("versao-3-proibida")))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Documento oficial de instrumento formalizado é imutável."));
        mockMvc.perform(delete("/api/v1/documentos/{id}", documentoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Documento oficial de instrumento formalizado é imutável."));

        MvcResult download = mockMvc.perform(get(
                        "/api/v1/documentos/{id}/versoes/{versao}/arquivo", documentoId, 2)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(versaoOficial);
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.documentoAssinadoVersao").value(2))
                .andExpect(jsonPath("$.instrumento.documentoAssinadoChecksumSha256")
                        .value(checksumOficial));
    }

    @Test
    void novaVersaoConcorrenteComFormalizacaoRespeitaEstadoConfirmadoDoDocumento()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-VERSAO-FORMALIZACAO-CONCORRENTE-016");
        long documentoId = criarDocumento(
                token, processoId, "ASSINADO", "instrumento.pdf", pdfValido("versao-1"));
        DocumentoRepository.HierarquiaDocumento hierarquiaAntesDaFormalizacao = documentos
                .findHierarquiaById(documentoId)
                .orElseThrow();
        CountDownLatch hierarquiaInicialLida = new CountDownLatch(1);
        CountDownLatch liberarNovaVersao = new CountDownLatch(1);
        AtomicInteger consultasDaHierarquia = new AtomicInteger();
        AtomicReference<Long> instrumentoFormalizado = new AtomicReference<>();
        doAnswer(invocacao -> {
            if (consultasDaHierarquia.incrementAndGet() == 1) {
                hierarquiaInicialLida.countDown();
                if (!liberarNovaVersao.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("A formalização não liberou a mutação concorrente.");
                }
                return java.util.Optional.of(hierarquiaAntesDaFormalizacao);
            }
            Long instrumentoId = instrumentoFormalizado.get();
            if (instrumentoId == null) {
                throw new AssertionError("A hierarquia foi relida antes da formalização.");
            }
            return java.util.Optional.of(new DocumentoRepository.HierarquiaDocumento() {
                @Override
                public Long getDocumentoId() {
                    return documentoId;
                }

                @Override
                public ProprietarioDocumento getProprietarioTipo() {
                    return ProprietarioDocumento.INSTRUMENTO;
                }

                @Override
                public Long getProprietarioId() {
                    return instrumentoId;
                }
            });
        }).when(documentos).findHierarquiaById(eq(documentoId));

        MvcResult tentativaDeNovaVersao;
        MvcResult formalizado;
        try (var executor = Executors.newSingleThreadExecutor()) {
            var novaVersao = executor.submit(() -> mockMvc.perform(multipart(
                                    "/api/v1/documentos/{id}/versoes", documentoId)
                            .file(new MockMultipartFile(
                                    "arquivo", "instrumento-v2.pdf",
                                    MediaType.APPLICATION_PDF_VALUE,
                                    pdfValido("versao-2-concorrente")))
                            .header("Authorization", bearer(token)))
                    .andReturn());
            try {
                assertThat(hierarquiaInicialLida.await(10, TimeUnit.SECONDS)).isTrue();
                formalizado = mockMvc.perform(post(
                                "/api/v1/processos/{id}/instrumento", processoId)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(formalizacao(documentoId).toString()))
                        .andExpect(status().isCreated())
                        .andReturn();
                instrumentoFormalizado.set(json(formalizado).path("id").asLong());
            } finally {
                liberarNovaVersao.countDown();
            }
            tentativaDeNovaVersao = novaVersao.get(10, TimeUnit.SECONDS);
        }

        assertThat(tentativaDeNovaVersao.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(tentativaDeNovaVersao).path("mensagem").asText())
                .isEqualTo("Documento oficial de instrumento formalizado é imutável.");
        long instrumentoId = instrumentoFormalizado.get();
        mockMvc.perform(get("/api/v1/documentos")
                        .queryParam("proprietarioTipo", "INSTRUMENTO")
                        .queryParam("proprietarioId", Long.toString(instrumentoId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(documentoId))
                .andExpect(jsonPath("$[0].ativo").value(true))
                .andExpect(jsonPath("$[0].versoes.length()").value(1));
    }

    private long criarProcesso(String token, String numero) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "numero", numero,
                                "origem", "DIPAC"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataCadastro").value(HOJE.toString()))
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
            byte[] conteudo) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", nome, MediaType.APPLICATION_OCTET_STREAM_VALUE, conteudo);
        MvcResult resultado = mockMvc.perform(multipart("/api/v1/documentos")
                        .file(arquivo)
                        .param("proprietarioTipo", "PROCESSO")
                        .param("proprietarioId", Long.toString(processoId))
                        .param("categoria", categoria)
                        .param("titulo", nome)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criadoEm").value(AGORA_JSON))
                .andExpect(jsonPath("$.versoes[0].criadoEm").value(AGORA_JSON))
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

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        RelogioControlavel relogioFixo() {
            return new RelogioControlavel(HOJE);
        }
    }

}
