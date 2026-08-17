package com.moments.sicc.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SiccApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fluxoHttpCriticoPreservaSegurancaDominioEContratoPublico() throws Exception {
        mockMvc.perform(get("/api/v1/public/processos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/processos"))
                .andExpect(status().isUnauthorized());

        MvcResult primeiroLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"admin","senha":"Temporaria123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trocaSenhaObrigatoria").value(true))
                .andReturn();
        String tokenTemporario = json(primeiroLogin).get("token").asText();

        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", "Bearer " + tokenTemporario))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", "Bearer " + tokenTemporario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Temporaria123!","novaSenha":"Permanente123!"}
                                """))
                .andExpect(status().isNoContent());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"admin","senha":"Permanente123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ADMINISTRADOR_DIPAC"))
                .andExpect(jsonPath("$.trocaSenhaObrigatoria").value(false))
                .andReturn();
        String token = json(login).get("token").asText();

        mockMvc.perform(post("/api/v1/admin/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Operador DIPAC",
                                  "email":"operador@ufgd.edu.br",
                                  "login":"operador",
                                  "senhaTemporaria":"Operador123!",
                                  "perfil":"OPERADOR_DIPAC"
                                }
                                """))
                .andExpect(status().isCreated());
        MvcResult loginOperadorTemporario = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"operador","senha":"Operador123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trocaSenhaObrigatoria").value(true))
                .andReturn();
        String tokenOperadorTemporario = json(loginOperadorTemporario).get("token").asText();
        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", "Bearer " + tokenOperadorTemporario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Operador123!","novaSenha":"Operador456!"}
                                """))
                .andExpect(status().isNoContent());
        MvcResult loginOperador = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"operador","senha":"Operador456!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String tokenOperador = json(loginOperador).get("token").asText();
        mockMvc.perform(get("/api/v1/admin/usuarios")
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk());

        MvcResult setorCriado = mockMvc.perform(post("/api/v1/admin/setores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sigla":"DIPAC","nome":"Divisão de Parcerias e Convênios"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn();
        long setorId = json(setorCriado).get("id").asLong();

        MvcResult processoCriado = mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numero":"23005.000001/2026-10","origem":"DIPAC","numeroProjeto":"P-001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EM_FORMALIZACAO"))
                .andExpect(jsonPath("$.instrumento").doesNotExist())
                .andReturn();
        long processoId = json(processoCriado).get("id").asLong();

        mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numero":"23005.000001/2026-10","origem":"DIPAC"}
                                """))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/public/processos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numeroProcesso").value("23005.000001/2026-10"))
                .andExpect(jsonPath("$.content[0].tipoInstrumento").value("Ainda não formalizado"))
                .andExpect(jsonPath("$.content[0].formalizado").doesNotExist())
                .andExpect(jsonPath("$.content[0].situacaoContratual").doesNotExist())
                .andExpect(jsonPath("$.content[0].situacaoTed").doesNotExist())
                .andExpect(jsonPath("$.content[0].valorAtual").doesNotExist())
                .andExpect(jsonPath("$.content[0].participes").doesNotExist())
                .andExpect(jsonPath("$.content[0].documentos").doesNotExist());

        MockMultipartFile pdf = new MockMultipartFile(
                "arquivo", "instrumento-assinado.pdf", "application/octet-stream", "%PDF-1.4\n%%EOF".getBytes());
        MvcResult documento = mockMvc.perform(multipart("/api/v1/documentos")
                        .file(pdf)
                        .param("proprietarioTipo", "PROCESSO")
                        .param("proprietarioId", Long.toString(processoId))
                        .param("categoria", "ASSINADO")
                        .param("titulo", "Instrumento assinado")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versoes[0].tipoMime").value("application/pdf"))
                .andReturn();
        long documentoId = json(documento).get("id").asLong();

        MockMultipartFile falsoPdf = new MockMultipartFile(
                "arquivo", "disfarcado.pdf", "application/pdf", "não é pdf".getBytes());
        mockMvc.perform(multipart("/api/v1/documentos")
                        .file(falsoPdf)
                        .param("proprietarioTipo", "PROCESSO")
                        .param("proprietarioId", Long.toString(processoId))
                        .param("categoria", "ASSINADO")
                        .param("titulo", "Arquivo inválido")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        String formalizacao = objectMapper.writeValueAsString(java.util.Map.ofEntries(
                java.util.Map.entry("numero", "CG-001/2026"),
                java.util.Map.entry("tipo", "CONTRATO_GESTAO"),
                java.util.Map.entry("objeto", "Gestão de projeto institucional"),
                java.util.Map.entry("descricao", "Instrumento do projeto P-001"),
                java.util.Map.entry("natureza", "Administrativa"),
                java.util.Map.entry("coordenador", "Maria Silva"),
                java.util.Map.entry("participes", java.util.List.of("UFGD", "Fundação")),
                java.util.Map.entry("valorAtual", 150000),
                java.util.Map.entry("vigenciaContratualFinal", LocalDate.now().plusDays(365).toString()),
                java.util.Map.entry("vigenciaTedFinal", LocalDate.now().plusDays(200).toString()),
                java.util.Map.entry("dataFormalizacao", LocalDate.now().toString()),
                java.util.Map.entry("documentoAssinadoId", documentoId)));
        MvcResult formalizado = mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formalizacao))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacaoContratual").value("VALIDA"))
                .andExpect(jsonPath("$.situacaoTed").value("VALIDA"))
                .andReturn();
        long instrumentoId = json(formalizado).get("id").asLong();

        String movimento = objectMapper.writeValueAsString(java.util.Map.of(
                "contextoTipo", "FORMALIZACAO", "contextoId", processoId,
                "dataMovimentacao", LocalDate.now().toString(), "setorDestinoId", setorId,
                "observacao", "Entrada na DIPAC"));
        mockMvc.perform(post("/api/v1/movimentacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(movimento))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenciaDiaria").value(1));
        mockMvc.perform(post("/api/v1/movimentacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(movimento))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenciaDiaria").value(2));
        mockMvc.perform(get("/api/v1/notificacoes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        MvcResult rascunho = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-01/2026",
                                "operacao", "ORIGINAL",
                                "mudancas", java.util.List.of(
                                        java.util.Map.of(
                                                "campo", "VALOR_ATUAL",
                                                "valorAnterior", "150000.00",
                                                "valorNovo", "175000.00"),
                                        java.util.Map.of(
                                                "campo", "COORDENADOR",
                                                "valorAnterior", "Maria Silva",
                                                "valorNovo", "João Souza"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RASCUNHO"))
                .andReturn();
        long alteracaoId = json(rascunho).get("id").asLong();
        MockMultipartFile pdfTermo = new MockMultipartFile(
                "arquivo", "termo-assinado.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes());
        MvcResult documentoTermo = mockMvc.perform(multipart("/api/v1/documentos")
                        .file(pdfTermo)
                        .param("proprietarioTipo", "TERMO_ADITIVO")
                        .param("proprietarioId", Long.toString(alteracaoId))
                        .param("categoria", "ASSINADO")
                        .param("titulo", "Termo Aditivo assinado")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long documentoTermoId = json(documentoTermo).get("id").asLong();
        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", alteracaoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "dataEfetivacao", LocalDate.now().toString(),
                                "ordemOficial", 1,
                                "documentoAssinadoId", documentoTermoId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EFETIVADA"));
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.valorAtual").value(175000.00))
                .andExpect(jsonPath("$.instrumento.coordenador").value("João Souza"));

        MvcResult retificacao = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-02/2026",
                                "operacao", "RETIFICACAO",
                                "referenciaId", alteracaoId,
                                "mudancas", java.util.List.of(java.util.Map.of(
                                        "campo", "VALOR_ATUAL",
                                        "valorAnterior", "175000.00",
                                        "valorNovo", "180000.00"))))))
                .andExpect(status().isCreated())
                .andReturn();
        long retificacaoId = json(retificacao).get("id").asLong();
        long documentoRetificacaoId = criarDocumentoAssinado(token, "TERMO_ADITIVO", retificacaoId, "retificacao.pdf");
        efetivarAlteracao(token, retificacaoId, documentoRetificacaoId, 2);
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.valorAtual").value(180000.00))
                .andExpect(jsonPath("$.instrumento.coordenador").value("João Souza"));

        MvcResult cancelamento = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-03/2026",
                                "operacao", "CANCELAMENTO",
                                "referenciaId", retificacaoId))))
                .andExpect(status().isCreated())
                .andReturn();
        long cancelamentoId = json(cancelamento).get("id").asLong();
        long documentoCancelamentoId = criarDocumentoAssinado(token, "TERMO_ADITIVO", cancelamentoId, "cancelamento.pdf");
        efetivarAlteracao(token, cancelamentoId, documentoCancelamentoId, 3);
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.valorAtual").value(175000.00))
                .andExpect(jsonPath("$.instrumento.coordenador").value("João Souza"));

        MvcResult retificacaoDependente = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-04/2026",
                                "operacao", "RETIFICACAO",
                                "referenciaId", alteracaoId,
                                "mudancas", java.util.List.of(java.util.Map.of(
                                        "campo", "VALOR_ATUAL",
                                        "valorAnterior", "175000.00",
                                        "valorNovo", "190000.00"))))))
                .andExpect(status().isCreated())
                .andReturn();
        long retificacaoDependenteId = json(retificacaoDependente).get("id").asLong();
        long documentoRetificacaoDependenteId = criarDocumentoAssinado(
                token, "TERMO_ADITIVO", retificacaoDependenteId, "retificacao-dependente.pdf");
        efetivarAlteracao(token, retificacaoDependenteId, documentoRetificacaoDependenteId, 4);

        MvcResult cancelamentoOriginal = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-05/2026",
                                "operacao", "CANCELAMENTO",
                                "referenciaId", alteracaoId))))
                .andExpect(status().isCreated())
                .andReturn();
        long cancelamentoOriginalId = json(cancelamentoOriginal).get("id").asLong();
        long documentoCancelamentoOriginalId = criarDocumentoAssinado(
                token, "TERMO_ADITIVO", cancelamentoOriginalId, "cancelamento-original.pdf");
        efetivarAlteracao(token, cancelamentoOriginalId, documentoCancelamentoOriginalId, 5);
        mockMvc.perform(get("/api/v1/processos/{id}", processoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumento.valorAtual").value(150000.00))
                .andExpect(jsonPath("$.instrumento.coordenador").value("Maria Silva"));
        mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-06/2026",
                                "operacao", "RETIFICACAO",
                                "referenciaId", retificacaoDependenteId,
                                "mudancas", java.util.List.of(java.util.Map.of(
                                        "campo", "VALOR_ATUAL",
                                        "valorAnterior", "150000.00",
                                        "valorNovo", "200000.00"))))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/public/processos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].formalizado").doesNotExist())
                .andExpect(jsonPath("$.content[0].status").value("EM_VIGENCIA"))
                .andExpect(jsonPath("$.content[0].coordenador").value("Maria Silva"))
                .andExpect(jsonPath("$.content[0].valorAtual").doesNotExist());

        mockMvc.perform(get("/api/v1/processos")
                        .queryParam("origem", "DIPAC")
                        .queryParam("tipo", "CONTRATO_GESTAO")
                        .queryParam("status", "EM_VIGENCIA")
                        .queryParam("vigencia", "VALIDA")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        var cabecalhos = java.util.Map.of(
                "ANUAL_PROCESSOS", "ano;total;em_formalizacao",
                "INSTRUMENTOS_POR_TIPO", "tipo_instrumento;quantidade;valor_total_atual",
                "HISTORICO_TRAMITACOES", "contexto;contexto_id;data;sequencia",
                "VIGENCIAS", "numero_processo;tipo_instrumento;vigencia_contratual",
                "CONSOLIDADO", "numero_processo;origem;status;tipo_instrumento");
        for (var esperado : cabecalhos.entrySet()) {
            MvcResult gerado = mockMvc.perform(post("/api/v1/relatorios")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "tipo", esperado.getKey(),
                                    "formato", "CSV",
                                    "filtros", java.util.Map.of("origem", "DIPAC")))))
                    .andExpect(status().isCreated())
                    .andReturn();
            long relatorioId = json(gerado).get("id").asLong();
            MvcResult arquivo = mockMvc.perform(get("/api/v1/relatorios/{id}/arquivo", relatorioId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            assertTrue(arquivo.getResponse().getContentAsString().startsWith(esperado.getValue()));
        }

        mockMvc.perform(delete("/api/v1/processos/{id}", processoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(get("/api/v1/public/processos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private long criarDocumentoAssinado(
            String token, String proprietarioTipo, long proprietarioId, String nome) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", nome, "application/pdf", "%PDF-1.4\n%%EOF".getBytes());
        MvcResult resultado = mockMvc.perform(multipart("/api/v1/documentos")
                        .file(arquivo)
                        .param("proprietarioTipo", proprietarioTipo)
                        .param("proprietarioId", Long.toString(proprietarioId))
                        .param("categoria", "ASSINADO")
                        .param("titulo", nome)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private void efetivarAlteracao(
            String token, long alteracaoId, long documentoId, int ordem) throws Exception {
        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", alteracaoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "dataEfetivacao", LocalDate.now().toString(),
                                "ordemOficial", ordem,
                                "documentoAssinadoId", documentoId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EFETIVADA"));
    }
}
