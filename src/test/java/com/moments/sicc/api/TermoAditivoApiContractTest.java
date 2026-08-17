package com.moments.sicc.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
        "spring.datasource.url=jdbc:h2:mem:sicc-termo-aditivo;MODE=PostgreSQL")
@AutoConfigureMockMvc
@Import(TermoAditivoApiContractTest.RelogioFixoConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TermoAditivoApiContractTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 8, 7);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void operadorPreparaTermoComMudancasExplicitasSemAlterarInstrumentoAtual() throws Exception {
        String tokenAdministrador = tokenAdministradorPermanente();
        String tokenOperador = criarOperador(tokenAdministrador);
        long processoId = criarProcesso(tokenAdministrador, "PROC-TA-012");
        long documentoId = criarDocumentoAssinado(tokenAdministrador, processoId);
        long instrumentoId = formalizar(tokenAdministrador, processoId, documentoId);

        MvcResult criado = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(tokenOperador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-01/2026",
                                "operacao", "ORIGINAL",
                                "mudancas", List.of(
                                        Map.of(
                                                "campo", "VALOR_ATUAL",
                                                "valorAnterior", "150000.00",
                                                "valorNovo", "175000.00"),
                                        Map.of(
                                                "campo", "VIGENCIA_CONTRATUAL_FINAL",
                                                "valorAnterior", "2027-08-01",
                                                "valorNovo", "2028-08-01"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("TERMO_ADITIVO"))
                .andExpect(jsonPath("$.estado").value("RASCUNHO"))
                .andExpect(jsonPath("$.mudancas.length()").value(2))
                .andExpect(jsonPath("$.mudancas[0].campo").value("VALOR_ATUAL"))
                .andExpect(jsonPath("$.mudancas[0].valorAnterior").value("150000.00"))
                .andExpect(jsonPath("$.mudancas[0].valorNovo").value("175000.00"))
                .andExpect(jsonPath("$.tramitacao.movimentacoes").isEmpty())
                .andReturn();
        long termoId = json(criado).get("id").asLong();

        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_VIGENCIA"))
                .andExpect(jsonPath("$.instrumento.valorAtual").value(150000.00))
                .andExpect(jsonPath("$.instrumento.vigenciaContratualFinal").value("2027-08-01"));
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "CRIAR_TERMO_ADITIVO")
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].objeto.tipo").value("TERMO_ADITIVO"))
                .andExpect(jsonPath("$.content[0].objeto.id").value(termoId));
    }

    @Test
    void operadorEditaSomenteRascunhoEValorAnteriorDeveRefletirEstadoAtual() throws Exception {
        String tokenAdministrador = tokenAdministradorPermanente();
        long processoId = criarProcesso(tokenAdministrador, "PROC-TA-EDIT-012");
        long documentoId = criarDocumentoAssinado(tokenAdministrador, processoId);
        long instrumentoId = formalizar(tokenAdministrador, processoId, documentoId);
        long termoId = criarTermo(tokenAdministrador, instrumentoId, "TA-EDIT-01/2026",
                List.of(Map.of(
                        "campo", "VALOR_ATUAL",
                        "valorAnterior", "150000.00",
                        "valorNovo", "175000.00")));

        mockMvc.perform(put("/api/v1/alteracoes/{id}", termoId)
                        .header("Authorization", bearer(tokenAdministrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "numeroOficial", "TA-EDIT-02/2026",
                                "mudancas", List.of(Map.of(
                                        "campo", "COORDENADOR",
                                        "valorAnterior", "Maria Silva",
                                        "valorNovo", "Ana Souza"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroOficial").value("TA-EDIT-02/2026"))
                .andExpect(jsonPath("$.mudancas.length()").value(1))
                .andExpect(jsonPath("$.mudancas[0].campo").value("COORDENADOR"))
                .andExpect(jsonPath("$.mudancas[0].valorAnterior").value("Maria Silva"))
                .andExpect(jsonPath("$.mudancas[0].valorNovo").value("Ana Souza"));

        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.valorAtual").value(150000.00))
                .andExpect(jsonPath("$.instrumento.coordenador").value("Maria Silva"));
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "EDITAR_TERMO_ADITIVO")
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].objeto.id").value(termoId));

        mockMvc.perform(put("/api/v1/alteracoes/{id}", termoId)
                        .header("Authorization", bearer(tokenAdministrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "numeroOficial", "TA-EDIT-03/2026",
                                "mudancas", List.of(Map.of(
                                        "campo", "COORDENADOR",
                                        "valorAnterior", "Coordenador desatualizado",
                                        "valorNovo", "Ana Souza"))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        "O valor anterior de COORDENADOR não corresponde ao estado atual do Instrumento Contratual."));
    }

    @Test
    void catalogoFechadoProtegeIdentidadeEValidaTipoDoNovoValor() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-TIPO-012");
        long documentoId = criarDocumentoAssinado(token, processoId);
        long instrumentoId = formalizar(token, processoId, documentoId);

        mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-TIPO-01/2026",
                                "operacao", "ORIGINAL",
                                "mudancas", List.of(Map.of(
                                        "campo", "NUMERO",
                                        "valorAnterior", "CV-012/2026",
                                        "valorNovo", "CV-999/2026"))))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-TIPO-02/2026",
                                "operacao", "ORIGINAL",
                                "mudancas", List.of(Map.of(
                                        "campo", "VALOR_ATUAL",
                                        "valorAnterior", "150000.00",
                                        "valorNovo", "cento e setenta mil"))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        "O novo valor de VALOR_ATUAL deve ser um número decimal não negativo."));

        mockMvc.perform(get("/api/v1/alteracoes")
                        .queryParam("instrumentoId", Long.toString(instrumentoId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void termoMantemTramitacaoLivreContinuaESeparadaDosDemaisHistoricos() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-TRAM-012");
        long documentoId = criarDocumentoAssinado(token, processoId);
        long instrumentoId = formalizar(token, processoId, documentoId);
        long primeiroTermoId = criarTermo(token, instrumentoId, "TA-TRAM-01/2026",
                List.of(Map.of(
                        "campo", "COORDENADOR",
                        "valorAnterior", "Maria Silva",
                        "valorNovo", "Ana Souza")));
        long segundoTermoId = criarTermo(token, instrumentoId, "TA-TRAM-02/2026",
                List.of(Map.of(
                        "campo", "NATUREZA",
                        "valorAnterior", "Administrativa",
                        "valorNovo", "Acadêmica")));
        long dipacId = criarSetor(token, "DIPAC", "Divisão de Parcerias");
        long proapId = criarSetor(token, "PROAP", "Pró-Reitoria de Administração");

        movimentar(token, "FORMALIZACAO", processoId, HOJE.minusDays(3), dipacId, "Formalização")
                .andExpect(status().isCreated());
        movimentar(token, "TERMO_ADITIVO", primeiroTermoId, HOJE.minusDays(2), dipacId, "Preparação")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenciaDiaria").value(1));
        movimentar(token, "TERMO_ADITIVO", primeiroTermoId, HOJE.minusDays(2), proapId, "Análise")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenciaDiaria").value(2));
        movimentar(token, "TERMO_ADITIVO", segundoTermoId, HOJE.minusDays(1), dipacId, "Outro termo")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenciaDiaria").value(1));
        movimentar(token, "TERMO_ADITIVO", primeiroTermoId, HOJE.plusDays(1), dipacId, "Data futura")
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/alteracoes/{id}", primeiroTermoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tramitacao.setorAtual.sigla").value("PROAP"))
                .andExpect(jsonPath("$.tramitacao.movimentacoes.length()").value(2))
                .andExpect(jsonPath("$.tramitacao.movimentacoes[0].sequenciaDiaria").value(1))
                .andExpect(jsonPath("$.tramitacao.movimentacoes[1].sequenciaDiaria").value(2));
        mockMvc.perform(get("/api/v1/alteracoes/{id}", segundoTermoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tramitacao.setorAtual.sigla").value("DIPAC"))
                .andExpect(jsonPath("$.tramitacao.movimentacoes.length()").value(1));
        mockMvc.perform(get("/api/v1/processos/{id}/tramitacao", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movimentacoes.length()").value(1))
                .andExpect(jsonPath("$.movimentacoes[0].contextoTipo").value("FORMALIZACAO"));
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "CRIAR_MOVIMENTACAO")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void somenteUmaEfetivacaoConcorrentePodeConfirmarOMesmoRascunho() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-CONCORRENTE-012");
        long documentoInstrumentoId = criarDocumentoAssinado(token, processoId);
        long instrumentoId = formalizar(token, processoId, documentoInstrumentoId);
        long termoId = criarTermo(token, instrumentoId, "TA-CONCORRENTE-01/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL",
                "valorAnterior", "150000.00",
                "valorNovo", "175000.00")));
        long documentoTermoId = criarDocumentoDoTermo(token, termoId);
        CountDownLatch inicio = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> efetivarStatus(
                    inicio, token, termoId, documentoTermoId));
            var segunda = executor.submit(() -> efetivarStatus(
                    inicio, token, termoId, documentoTermoId));
            inicio.countDown();
            List<Integer> resultados = java.util.stream.Stream.of(primeira.get(), segunda.get())
                    .sorted()
                    .toList();
            assertEquals(List.of(200, 422), resultados);
        }
    }

    @Test
    void efetivacaoVinculaPdfTornaEvidenciasImutaveisEExplicaResultado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-EFETIVAR-013");
        long documentoInstrumentoId = criarDocumentoAssinado(token, processoId);
        long instrumentoId = formalizar(token, processoId, documentoInstrumentoId);
        long termoId = criarTermo(token, instrumentoId, "TA-EFETIVAR-01/2026", List.of(
                Map.of(
                        "campo", "VALOR_ATUAL",
                        "valorAnterior", "150000.00",
                        "valorNovo", "175000.00"),
                Map.of(
                        "campo", "COORDENADOR",
                        "valorAnterior", "Maria Silva",
                        "valorNovo", "Ana Souza")));
        long documentoTermoId = criarDocumentoDoTermo(token, termoId);

        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", termoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEfetivacao", HOJE.toString(),
                                "ordemOficial", 1,
                                "documentoAssinadoId", documentoTermoId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EFETIVADA"))
                .andExpect(jsonPath("$.documentoAssinadoId").value(documentoTermoId))
                .andExpect(jsonPath("$.estadoAtualInstrumento.valorAtual").value(175000.00))
                .andExpect(jsonPath("$.estadoAtualInstrumento.coordenador").value("Ana Souza"))
                .andExpect(jsonPath("$.estadoAtualInstrumento.statusProcesso").value("EM_VIGENCIA"))
                .andExpect(jsonPath("$.estadoAtualInstrumento.precedenciaPorCampo.VALOR_ATUAL.dataEfetivacao")
                        .value(HOJE.toString()))
                .andExpect(jsonPath("$.estadoAtualInstrumento.precedenciaPorCampo.VALOR_ATUAL.ordemOficial")
                        .value(1));

        mockMvc.perform(put("/api/v1/alteracoes/{id}", termoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "numeroOficial", "TA-EDITADO-INDEVIDAMENTE",
                                "mudancas", List.of(Map.of(
                                        "campo", "COORDENADOR",
                                        "valorAnterior", "Ana Souza",
                                        "valorNovo", "Outra pessoa"))))))
                .andExpect(status().isUnprocessableEntity());

        MockMultipartFile novaVersao = new MockMultipartFile(
                "arquivo", "termo-substituto.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4\nsubstituto\n%%EOF".getBytes());
        mockMvc.perform(multipart("/api/v1/documentos/{id}/versoes", documentoTermoId)
                        .file(novaVersao)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        "Documento oficial de alteração efetivada é imutável."));
        mockMvc.perform(delete("/api/v1/documentos/{id}", documentoTermoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        "Documento oficial de alteração efetivada é imutável."));

        mockMvc.perform(get("/api/v1/alteracoes/{id}", termoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroOficial").value("TA-EFETIVAR-01/2026"))
                .andExpect(jsonPath("$.mudancas[0].valorNovo").value("175000.00"))
                .andExpect(jsonPath("$.mudancas[1].valorNovo").value("Ana Souza"));
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "EFETIVAR_ALTERACAO")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].objeto.id").value(termoId));
    }

    @Test
    void efetivacaoRejeitaOrdemOficialNaoPositiva() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-ORDEM-013");
        long documentoInstrumentoId = criarDocumentoAssinado(token, processoId);
        long instrumentoId = formalizar(token, processoId, documentoInstrumentoId);
        long termoId = criarTermo(token, instrumentoId, "TA-ORDEM-01/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL",
                "valorAnterior", "150000.00",
                "valorNovo", "175000.00")));
        long documentoTermoId = criarDocumentoDoTermo(token, termoId);

        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", termoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEfetivacao", HOJE.toString(),
                                "ordemOficial", 0,
                                "documentoAssinadoId", documentoTermoId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void estadoAtualUsaDataMaisRecenteEOrdemOficialComoDesempatePorCampo() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-PREVALENCIA-013");
        long documentoInstrumentoId = criarDocumentoAssinado(token, processoId);
        long instrumentoId = formalizar(token, processoId, documentoInstrumentoId);

        long termoMaisRecente = criarTermo(token, instrumentoId, "TA-RECENTE-01/2026", List.of(
                Map.of(
                        "campo", "VALOR_ATUAL",
                        "valorAnterior", "150000.00",
                        "valorNovo", "160000.00"),
                Map.of(
                        "campo", "COORDENADOR",
                        "valorAnterior", "Maria Silva",
                        "valorNovo", "Ana Souza")));
        long termoMaisAntigo = criarTermo(token, instrumentoId, "TA-ANTIGO-01/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL",
                "valorAnterior", "150000.00",
                "valorNovo", "155000.00")));
        efetivar(token, termoMaisRecente, criarDocumentoDoTermo(token, termoMaisRecente), HOJE, 1);
        efetivar(token, termoMaisAntigo, criarDocumentoDoTermo(token, termoMaisAntigo), HOJE.minusDays(1), 99);

        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.valorAtual").value(160000.00))
                .andExpect(jsonPath("$.instrumento.coordenador").value("Ana Souza"));

        long termoOrdemMenor = criarTermo(token, instrumentoId, "TA-EMPATE-01/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL",
                "valorAnterior", "160000.00",
                "valorNovo", "170000.00")));
        long termoOrdemMaior = criarTermo(token, instrumentoId, "TA-EMPATE-02/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL",
                "valorAnterior", "160000.00",
                "valorNovo", "180000.00")));
        efetivar(token, termoOrdemMaior, criarDocumentoDoTermo(token, termoOrdemMaior), HOJE, 3);
        efetivar(token, termoOrdemMenor, criarDocumentoDoTermo(token, termoOrdemMenor), HOJE, 2);

        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.valorAtual").value(180000.00))
                .andExpect(jsonPath("$.instrumento.coordenador").value("Ana Souza"));
        mockMvc.perform(get("/api/v1/alteracoes")
                        .queryParam("instrumentoId", Long.toString(instrumentoId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dataEfetivacao").value(HOJE.minusDays(1).toString()))
                .andExpect(jsonPath("$[1].ordemOficial").value(1))
                .andExpect(jsonPath("$[2].ordemOficial").value(2))
                .andExpect(jsonPath("$[3].ordemOficial").value(3))
                .andExpect(jsonPath("$[3].estadoAtualInstrumento.valorAtual").value(180000.00));
    }

    @Test
    void prorrogacaoReativaProcessoConcluidoSemEncerrarTramitacaoDoTermo() throws Exception {
        String token = tokenAdministradorPermanente();
        long dipacId = criarSetor(token, "DIP13", "Divisão de Parcerias 13");
        long proapId = criarSetor(token, "PRA13", "Pró-Reitoria Administrativa 13");
        long processoId = criarProcesso(token, "PROC-TA-REATIVAR-013");
        long documentoInstrumentoId = criarDocumentoAssinado(token, processoId);
        long instrumentoId = formalizar(
                token, processoId, documentoInstrumentoId, HOJE.minusDays(1));
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        long termoId = criarTermo(token, instrumentoId, "TA-REATIVAR-01/2026", List.of(Map.of(
                "campo", "VIGENCIA_CONTRATUAL_FINAL",
                "valorAnterior", HOJE.minusDays(1).toString(),
                "valorNovo", HOJE.plusYears(1).toString())));
        movimentar(token, "TERMO_ADITIVO", termoId, HOJE.minusDays(1), dipacId, "Preparação")
                .andExpect(status().isCreated());
        efetivar(token, termoId, criarDocumentoDoTermo(token, termoId), HOJE, 1);
        movimentar(token, "TERMO_ADITIVO", termoId, HOJE, proapId, "Continuidade após efetivação")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_VIGENCIA"))
                .andExpect(jsonPath("$.instrumento.vigenciaContratualFinal")
                        .value(HOJE.plusYears(1).toString()));
        mockMvc.perform(get("/api/v1/alteracoes/{id}", termoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EFETIVADA"))
                .andExpect(jsonPath("$.tramitacao.setorAtual.sigla").value("PRA13"))
                .andExpect(jsonPath("$.tramitacao.movimentacoes.length()").value(2));
    }

    @Test
    void termoQuePassaraAPrevalecerExigeRevisaoSeValorAnteriorFicouObsoleto() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-OBSOLETO-013");
        long documentoInstrumentoId = criarDocumentoAssinado(token, processoId);
        long instrumentoId = formalizar(token, processoId, documentoInstrumentoId);
        long termoObsoleto = criarTermo(token, instrumentoId, "TA-OBSOLETO-01/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL",
                "valorAnterior", "150000.00",
                "valorNovo", "175000.00")));
        long termoIntermediario = criarTermo(token, instrumentoId, "TA-INTERMEDIARIO-01/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL",
                "valorAnterior", "150000.00",
                "valorNovo", "160000.00")));
        efetivar(token, termoIntermediario, criarDocumentoDoTermo(token, termoIntermediario),
                HOJE.minusDays(1), 1);

        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", termoObsoleto)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEfetivacao", HOJE.toString(),
                                "ordemOficial", 1,
                                "documentoAssinadoId", criarDocumentoDoTermo(token, termoObsoleto)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        "O valor anterior de VALOR_ATUAL ficou desatualizado; revise o rascunho antes de efetivar."));
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.valorAtual").value(160000.00));
        mockMvc.perform(get("/api/v1/alteracoes/{id}", termoObsoleto)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RASCUNHO"));
    }

    @Test
    void alteracaoCanceladaNaoImpedeValidacaoDaBaseDoTermoRetroativo() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-CANCELADA-013");
        long instrumentoId = formalizar(token, processoId, criarDocumentoAssinado(token, processoId));
        long candidato = criarTermo(token, instrumentoId, "TA-CANDIDATO-01/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL", "valorAnterior", "150000.00", "valorNovo", "175000.00")));
        long baseVigente = criarTermo(token, instrumentoId, "TA-BASE-01/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL", "valorAnterior", "150000.00", "valorNovo", "160000.00")));
        efetivar(token, baseVigente, criarDocumentoDoTermo(token, baseVigente), HOJE.minusDays(4), 1);
        long posteriorCancelada = criarTermo(token, instrumentoId, "TA-CANCELADA-01/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL", "valorAnterior", "160000.00", "valorNovo", "180000.00")));
        efetivar(token, posteriorCancelada, criarDocumentoDoTermo(token, posteriorCancelada), HOJE.minusDays(2), 1);
        long cancelamento = criarOperacao(token, instrumentoId, "CANCELA-TA-01/2026",
                "CANCELAMENTO", posteriorCancelada, List.of());
        efetivar(token, cancelamento, criarDocumentoDoTermo(token, cancelamento), HOJE.minusDays(1), 1);

        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", candidato)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEfetivacao", HOJE.minusDays(3).toString(),
                                "ordemOficial", 1,
                                "documentoAssinadoId", criarDocumentoDoTermo(token, candidato)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        "O valor anterior de VALOR_ATUAL ficou desatualizado; revise o rascunho antes de efetivar."));
    }

    @Test
    void cancelamentosReconstroemValorFormalizadoMesmoComCadastroForaDaOrdemOficial()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-RECONSTRUCAO-015");
        long instrumentoId = formalizar(token, processoId, criarDocumentoAssinado(token, processoId));

        long termoPosterior = criarTermo(token, instrumentoId, "TA-POSTERIOR-015/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL", "valorAnterior", "150000.00", "valorNovo", "175000.00")));
        efetivar(token, termoPosterior, criarDocumentoDoTermo(token, termoPosterior), HOJE, 1);

        long termoRetroativo = criarTermo(token, instrumentoId, "TA-RETROATIVO-015/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL", "valorAnterior", "175000.00", "valorNovo", "160000.00")));
        efetivar(token, termoRetroativo, criarDocumentoDoTermo(token, termoRetroativo), HOJE.minusDays(1), 1);

        long cancelamentoPosterior = criarOperacao(token, instrumentoId, "CANCELA-POSTERIOR-015/2026",
                "CANCELAMENTO", termoPosterior, List.of());
        efetivar(token, cancelamentoPosterior, criarDocumentoDoTermo(token, cancelamentoPosterior), HOJE, 2);
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.valorAtual").value(160000.00));

        long cancelamentoRetroativo = criarOperacao(token, instrumentoId, "CANCELA-RETROATIVO-015/2026",
                "CANCELAMENTO", termoRetroativo, List.of());
        efetivar(token, cancelamentoRetroativo, criarDocumentoDoTermo(token, cancelamentoRetroativo), HOJE, 3);
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.valorAtual").value(150000.00));
    }

    @Test
    void apiExplicaCadeiaRetificacaoCancelamentoEAuditaCadaRecomputacao() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-CADEIA-015");
        long instrumentoId = formalizar(token, processoId, criarDocumentoAssinado(token, processoId));

        long original = criarTermo(token, instrumentoId, "TA-ORIGINAL-015/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL", "valorAnterior", "150000.00", "valorNovo", "175000.00")));
        efetivar(token, original, criarDocumentoDoTermo(token, original), HOJE, 1);
        long retificacao = criarOperacao(token, instrumentoId, "TA-RETIFICA-015/2026",
                "RETIFICACAO", original, List.of(Map.of(
                        "campo", "VALOR_ATUAL", "valorAnterior", "175000.00", "valorNovo", "180000.00")));
        efetivar(token, retificacao, criarDocumentoDoTermo(token, retificacao), HOJE, 2);
        long cancelamento = criarOperacao(token, instrumentoId, "TA-CANCELA-RETIFICA-015/2026",
                "CANCELAMENTO", retificacao, List.of());
        efetivar(token, cancelamento, criarDocumentoDoTermo(token, cancelamento), HOJE, 3);
        long posterior = criarTermo(token, instrumentoId, "TA-POSTERIOR-CADEIA-015/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL", "valorAnterior", "175000.00", "valorNovo", "200000.00")));
        efetivar(token, posterior, criarDocumentoDoTermo(token, posterior), HOJE, 4);

        mockMvc.perform(get("/api/v1/alteracoes/{id}", original)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cadeia.length()").value(3))
                .andExpect(jsonPath("$.cadeia[0].id").value(original))
                .andExpect(jsonPath("$.cadeia[0].produzEfeitoAtual").value(false))
                .andExpect(jsonPath("$.cadeia[0].valoresProduzidos.VALOR_ATUAL").value("175000.00"))
                .andExpect(jsonPath("$.cadeia[1].id").value(retificacao))
                .andExpect(jsonPath("$.cadeia[1].operacao").value("RETIFICACAO"))
                .andExpect(jsonPath("$.cadeia[1].produzEfeitoAtual").value(false))
                .andExpect(jsonPath("$.cadeia[1].valoresProduzidos.VALOR_ATUAL").value("180000.00"))
                .andExpect(jsonPath("$.cadeia[2].id").value(cancelamento))
                .andExpect(jsonPath("$.cadeia[2].operacao").value("CANCELAMENTO"))
                .andExpect(jsonPath("$.cadeia[2].valoresProduzidos.VALOR_ATUAL").value("175000.00"));

        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "RECOMPUTAR_ESTADO_INSTRUMENTO")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].objeto.tipo").value("INSTRUMENTO_CONTRATUAL"))
                .andExpect(jsonPath("$.content[0].objeto.id").value(instrumentoId));
    }

    @Test
    void efetivacaoRevalidaReferenciaDepoisDeOutroCancelamento() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-CANCELA-DUPLO-015");
        long instrumentoId = formalizar(token, processoId, criarDocumentoAssinado(token, processoId));
        long original = criarTermo(token, instrumentoId, "TA-CANCELA-DUPLO-015/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL", "valorAnterior", "150000.00", "valorNovo", "175000.00")));
        efetivar(token, original, criarDocumentoDoTermo(token, original), HOJE, 1);

        long primeiro = criarOperacao(token, instrumentoId, "CANCELA-1-015/2026",
                "CANCELAMENTO", original, List.of());
        long segundo = criarOperacao(token, instrumentoId, "CANCELA-2-015/2026",
                "CANCELAMENTO", original, List.of());
        efetivar(token, primeiro, criarDocumentoDoTermo(token, primeiro), HOJE, 2);

        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", segundo)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEfetivacao", HOJE.toString(),
                                "ordemOficial", 3,
                                "documentoAssinadoId", criarDocumentoDoTermo(token, segundo)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        "A referência já foi cancelada ou depende de uma alteração cancelada."));
    }

    @Test
    void operacaoReferenciadaDeveSerPosteriorNaCronologiaOficial() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-TA-ORDEM-REFERENCIA-015");
        long instrumentoId = formalizar(token, processoId, criarDocumentoAssinado(token, processoId));
        long original = criarTermo(token, instrumentoId, "TA-ORDEM-REFERENCIA-015/2026", List.of(Map.of(
                "campo", "VALOR_ATUAL", "valorAnterior", "150000.00", "valorNovo", "175000.00")));
        efetivar(token, original, criarDocumentoDoTermo(token, original), HOJE, 2);
        long cancelamento = criarOperacao(token, instrumentoId, "CANCELA-ORDEM-REFERENCIA-015/2026",
                "CANCELAMENTO", original, List.of());

        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", cancelamento)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEfetivacao", HOJE.toString(),
                                "ordemOficial", 1,
                                "documentoAssinadoId", criarDocumentoDoTermo(token, cancelamento)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        "A operação deve ser posterior à alteração de referência na cronologia oficial."));
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

    private org.springframework.test.web.servlet.ResultActions movimentar(
            String token,
            String contextoTipo,
            long contextoId,
            LocalDate data,
            long setorId,
            String observacao) throws Exception {
        return mockMvc.perform(post("/api/v1/movimentacoes")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "contextoTipo", contextoTipo,
                        "contextoId", contextoId,
                        "dataMovimentacao", data,
                        "setorDestinoId", setorId,
                        "observacao", observacao))));
    }

    private long criarTermo(
            String token, long instrumentoId, String numero, List<Map<String, String>> mudancas)
            throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", numero,
                                "operacao", "ORIGINAL",
                                "mudancas", mudancas))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private long criarOperacao(String token, long instrumentoId, String numero, String operacao,
            long referenciaId, List<Map<String, String>> mudancas) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", numero,
                                "operacao", operacao,
                                "referenciaId", referenciaId,
                                "mudancas", mudancas))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private long criarProcesso(String token, String numero) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "numero", numero,
                                "origem", "DIPAC"))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private long criarDocumentoAssinado(String token, long processoId) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "instrumento.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4\n%%EOF".getBytes());
        MvcResult resultado = mockMvc.perform(multipart("/api/v1/documentos")
                        .file(arquivo)
                        .param("proprietarioTipo", "PROCESSO")
                        .param("proprietarioId", Long.toString(processoId))
                        .param("categoria", "ASSINADO")
                        .param("titulo", "Instrumento assinado")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private long criarDocumentoDoTermo(String token, long termoId) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "termo-aditivo.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4\n%%EOF".getBytes());
        MvcResult resultado = mockMvc.perform(multipart("/api/v1/documentos")
                        .file(arquivo)
                        .param("proprietarioTipo", "TERMO_ADITIVO")
                        .param("proprietarioId", Long.toString(termoId))
                        .param("categoria", "ASSINADO")
                        .param("titulo", "Termo Aditivo assinado")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private int efetivarStatus(
            CountDownLatch inicio, String token, long termoId, long documentoTermoId) {
        try {
            inicio.await();
            return mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", termoId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "dataEfetivacao", HOJE.toString(),
                                    "ordemOficial", 1,
                                    "documentoAssinadoId", documentoTermoId))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void efetivar(
            String token, long termoId, long documentoTermoId, LocalDate data, int ordem)
            throws Exception {
        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", termoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEfetivacao", data.toString(),
                                "ordemOficial", ordem,
                                "documentoAssinadoId", documentoTermoId))))
                .andExpect(status().isOk());
    }

    private long formalizar(String token, long processoId, long documentoId) throws Exception {
        return formalizar(token, processoId, documentoId, LocalDate.of(2027, 8, 1));
    }

    private long formalizar(
            String token, long processoId, long documentoId, LocalDate vigenciaContratual)
            throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("numero", "CV-012/2026"),
                                Map.entry("tipo", "CONVENIO"),
                                Map.entry("objeto", "Cooperacao institucional"),
                                Map.entry("descricao", "Instrumento formalizado pela DIPAC"),
                                Map.entry("natureza", "Administrativa"),
                                Map.entry("coordenador", "Maria Silva"),
                                Map.entry("participes", List.of("UFGD", "Fundacao")),
                                Map.entry("valorAtual", 150000),
                                Map.entry("vigenciaContratualFinal", vigenciaContratual.toString()),
                                Map.entry("vigenciaTedFinal", "2027-04-30"),
                                Map.entry("dataFormalizacao", HOJE.toString()),
                                Map.entry("documentoAssinadoId", documentoId)))))
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
                                  "nome":"Operador DIPAC",
                                  "email":"operador-termo@sicc.test",
                                  "login":"operador-termo",
                                  "senhaTemporaria":"Operador123!",
                                  "perfil":"OPERADOR_DIPAC"
                                }
                                """))
                .andExpect(status().isCreated());
        String temporario = tokenDoLogin("operador-termo", "Operador123!", true);
        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", bearer(temporario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Operador123!","novaSenha":"Operador456!"}
                                """))
                .andExpect(status().isNoContent());
        return tokenDoLogin("operador-termo", "Operador456!", false);
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
                        .content(objectMapper.writeValueAsString(Map.of(
                                "login", login,
                                "senha", senha))))
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
            return Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
