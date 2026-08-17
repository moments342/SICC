package com.moments.sicc.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-apostilamento;MODE=PostgreSQL")
@AutoConfigureMockMvc
@Import(ApostilamentoApiContractTest.RelogioFixoConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApostilamentoApiContractTest extends DocumentoApiContractTestSupport {

    private static final LocalDate HOJE = LocalDate.of(2026, 8, 8);

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void catalogoDoApostilamentoAceitaSomenteCamposNaoContratuaisERejeitaIdentidade()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        long instrumentoId = formalizar(token, processoId, criarDocumentoDoProcesso(token, processoId));

        mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "APOSTILAMENTO",
                                "numeroOficial", "AP-LIMITE-01/2026",
                                "operacao", "ORIGINAL",
                                "mudancas", List.of(Map.of(
                                        "campo", "OBJETO",
                                        "valorAnterior", "Cooperação institucional",
                                        "valorNovo", "Objeto alterado"))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Apostilamento contém campo de natureza contratual."));

        mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrumentoId":%d,
                                  "tipo":"APOSTILAMENTO",
                                  "numeroOficial":"AP-IDENTIDADE-01/2026",
                                  "operacao":"ORIGINAL",
                                  "mudancas":[{
                                    "campo":"NUMERO_INSTRUMENTO",
                                    "valorAnterior":"CV-014/2026",
                                    "valorNovo":"CV-015/2026"
                                  }]
                                }
                                """.formatted(instrumentoId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "APOSTILAMENTO",
                                "numeroOficial", "AP-DUPLICADO-01/2026",
                                "operacao", "ORIGINAL",
                                "mudancas", List.of(
                                        Map.of("campo", "COORDENADOR", "valorAnterior", "Maria Silva",
                                                "valorNovo", "Ana Souza"),
                                        Map.of("campo", "COORDENADOR", "valorAnterior", "Maria Silva",
                                                "valorNovo", "Carla Lima"))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Cada campo pode aparecer uma única vez no Apostilamento."));

        MvcResult termoCriado = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-FRONTEIRA-01/2026",
                                "operacao", "ORIGINAL",
                                "mudancas", List.of(Map.of(
                                        "campo", "OBJETO",
                                        "valorAnterior", "Cooperação institucional",
                                        "valorNovo", "Objeto contratual alterado pelo termo"))))))
                .andExpect(status().isCreated())
                .andReturn();
        long termoId = json(termoCriado).get("id").asLong();
        long documentoTermoId = criarDocumentoDoTermo(token, termoId);
        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", termoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEfetivacao", HOJE,
                                "ordemOficial", 1,
                                "documentoAssinadoId", documentoTermoId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "APOSTILAMENTO",
                                "numeroOficial", "AP-CANCELA-TA-01/2026",
                                "operacao", "CANCELAMENTO",
                                "referenciaId", termoId,
                                "mudancas", List.of()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("A referência deve possuir o mesmo tipo da alteração."));

        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.objeto")
                        .value("Objeto contratual alterado pelo termo"))
                .andExpect(jsonPath("$.instrumento.coordenador").value("Maria Silva"));
    }

    @Test
    void operadorEditaTramitaEEfetivaApostilamentoSemEncerrarSuaTramitacao()
            throws Exception {
        String tokenAdministrador = tokenAdministradorPermanente();
        String token = criarOperador(tokenAdministrador);
        long processoId = criarProcesso(tokenAdministrador);
        long instrumentoId = formalizar(tokenAdministrador, processoId,
                criarDocumentoDoProcesso(tokenAdministrador, processoId));
        long setorDipac = criarSetor(tokenAdministrador, "DIP14", "Divisão de Parcerias");
        long setorProap = criarSetor(
                tokenAdministrador, "PRA14", "Pró-Reitoria de Administração");

        MvcResult criado = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "APOSTILAMENTO",
                                "numeroOficial", "AP-01/2026",
                                "operacao", "ORIGINAL",
                                "mudancas", List.of(Map.of(
                                        "campo", "COORDENADOR",
                                        "valorAnterior", "Maria Silva",
                                        "valorNovo", "Ana Souza"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("APOSTILAMENTO"))
                .andExpect(jsonPath("$.estado").value("RASCUNHO"))
                .andReturn();
        long apostilamentoId = json(criado).get("id").asLong();

        mockMvc.perform(put("/api/v1/alteracoes/{id}", apostilamentoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "numeroOficial", "AP-01/2026 revisado",
                                "mudancas", List.of(Map.of(
                                        "campo", "VIGENCIA_TED_FINAL",
                                        "valorAnterior", "2027-04-30",
                                        "valorNovo", "2027-10-31"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mudancas[0].campo").value("VIGENCIA_TED_FINAL"));

        movimentar(token, apostilamentoId, setorDipac, HOJE.minusDays(1), "Preparação")
                .andExpect(status().isCreated());
        long documentoId = criarDocumentoDoApostilamento(token, apostilamentoId);
        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", apostilamentoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEfetivacao", HOJE,
                                "ordemOficial", 1,
                                "documentoAssinadoId", documentoId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EFETIVADA"))
                .andExpect(jsonPath("$.documentoAssinadoId").value(documentoId))
                .andExpect(jsonPath("$.estadoAtualInstrumento.vigenciaTedFinal")
                        .value("2027-10-31"));

        mockMvc.perform(put("/api/v1/alteracoes/{id}", apostilamentoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "numeroOficial", "AP-ALTERADO-INDEVIDAMENTE",
                                "mudancas", List.of(Map.of(
                                        "campo", "COORDENADOR",
                                        "valorAnterior", "Maria Silva",
                                        "valorNovo", "Carla Lima"))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Alteração efetivada é imutável."));

        movimentar(token, apostilamentoId, setorProap, HOJE, "Continuidade após efetivação")
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/alteracoes/{id}", apostilamentoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tramitacao.setorAtual.sigla").value("PRA14"))
                .andExpect(jsonPath("$.tramitacao.movimentacoes.length()").value(2));
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_VIGENCIA"))
                .andExpect(jsonPath("$.instrumento.vigenciaTedFinal").value("2027-10-31"));
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "CRIAR_APOSTILAMENTO")
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "EDITAR_APOSTILAMENTO")
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "EFETIVAR_ALTERACAO")
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private long criarDocumentoDoProcesso(String token, long processoId) throws Exception {
        return criarDocumento(token, "PROCESSO", processoId, "instrumento.pdf", "Instrumento assinado");
    }

    private long criarDocumentoDoApostilamento(String token, long apostilamentoId) throws Exception {
        return criarDocumento(token, "APOSTILAMENTO", apostilamentoId,
                "apostilamento.pdf", "Apostilamento assinado");
    }

    private long criarDocumentoDoTermo(String token, long termoId) throws Exception {
        return criarDocumento(token, "TERMO_ADITIVO", termoId,
                "termo-aditivo.pdf", "Termo Aditivo assinado");
    }

    private long criarDocumento(
            String token, String proprietarioTipo, long proprietarioId,
            String nomeArquivo, String titulo) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", nomeArquivo, MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4\n%%EOF".getBytes());
        MvcResult resultado = mockMvc.perform(multipart("/api/v1/documentos")
                        .file(arquivo)
                        .param("proprietarioTipo", proprietarioTipo)
                        .param("proprietarioId", Long.toString(proprietarioId))
                        .param("categoria", "ASSINADO")
                        .param("titulo", titulo)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private long formalizar(String token, long processoId, long documentoId) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("numero", "CV-014/2026"),
                                Map.entry("tipo", "CONVENIO"),
                                Map.entry("objeto", "Cooperação institucional"),
                                Map.entry("descricao", "Instrumento formalizado pela DIPAC"),
                                Map.entry("natureza", "Administrativa"),
                                Map.entry("coordenador", "Maria Silva"),
                                Map.entry("participes", List.of("UFGD", "Fundação")),
                                Map.entry("valorAtual", 150000),
                                Map.entry("vigenciaContratualFinal", "2027-08-01"),
                                Map.entry("vigenciaTedFinal", "2027-04-30"),
                                Map.entry("dataFormalizacao", HOJE),
                                Map.entry("documentoAssinadoId", documentoId)))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private long criarSetor(String token, String sigla, String nome) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/admin/setores")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sigla", sigla,
                                "nome", nome))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private String criarOperador(String tokenAdministrador) throws Exception {
        mockMvc.perform(post("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenAdministrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Operador Apostilamento",
                                  "email":"operador-apostilamento@sicc.test",
                                  "login":"operador-apostilamento",
                                  "senhaTemporaria":"Operador123!",
                                  "perfil":"OPERADOR_DIPAC"
                                }
                                """))
                .andExpect(status().isCreated());
        String temporario = tokenDoLogin("operador-apostilamento", "Operador123!", true);
        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", bearer(temporario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Operador123!","novaSenha":"Operador456!"}
                                """))
                .andExpect(status().isNoContent());
        return tokenDoLogin("operador-apostilamento", "Operador456!", false);
    }

    private org.springframework.test.web.servlet.ResultActions movimentar(
            String token, long apostilamentoId, long setorId, LocalDate data, String observacao)
            throws Exception {
        return mockMvc.perform(post("/api/v1/movimentacoes")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "contextoTipo", "APOSTILAMENTO",
                        "contextoId", apostilamentoId,
                        "dataMovimentacao", data,
                        "setorDestinoId", setorId,
                        "observacao", observacao))));
    }

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
