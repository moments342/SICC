package com.moments.sicc.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-dashboard-portfolio;MODE=PostgreSQL")
@AutoConfigureMockMvc
@Import(DashboardPortfolioApiContractTest.RelogioFixoConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DashboardPortfolioApiContractTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 8, 8);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void painelConsultaSomenteEstadoAtualEAplicaOsMesmosFiltrosATodosOsIndicadores()
            throws Exception {
        String token = tokenAdministradorPermanente();

        criarProcesso(token, "PROC-FORMAL-016", "DIPAC");

        long processoProrrogado = criarProcesso(token, "PROC-PRORROGADO-016", "DIPAC");
        long instrumentoProrrogado = formalizar(
                token, processoProrrogado, "CONVENIO", 1_000,
                LocalDate.of(2026, 8, 7), LocalDate.of(2026, 12, 6),
                LocalDate.of(2026, 5, 15));
        efetivarTermo(token, instrumentoProrrogado, 1_000, 1_500,
                LocalDate.of(2026, 8, 7), LocalDate.of(2026, 12, 31));

        long processoConcluido = criarProcesso(token, "PROC-CONCLUIDO-016", "DIPAC");
        formalizar(token, processoConcluido, "CONTRATO_GESTAO", 2_000,
                LocalDate.of(2026, 7, 31), null, LocalDate.of(2026, 6, 20));

        long processoAlertaContratual = criarProcesso(token, "PROC-ALERTA-016", "PROAP");
        formalizar(token, processoAlertaContratual, "ACORDO_PARCERIA", 3_000,
                HOJE.plusDays(120), HOJE.minusDays(1), LocalDate.of(2026, 7, 10));

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processosPorStatus.EM_FORMALIZACAO").value(1))
                .andExpect(jsonPath("$.processosPorStatus.EM_VIGENCIA").value(2))
                .andExpect(jsonPath("$.processosPorStatus.CONCLUIDO").value(1))
                .andExpect(jsonPath("$.percentualConcluidos").value(25.0))
                .andExpect(jsonPath("$.alertasContratuais").value(1))
                .andExpect(jsonPath("$.alertasTed").value(1))
                .andExpect(jsonPath("$.valorTotalVigente").value(4_500.0))
                .andExpect(jsonPath("$.instrumentosPorTipo.CONTRATO_GESTAO").value(1))
                .andExpect(jsonPath("$.instrumentosPorTipo.CONVENIO").value(1))
                .andExpect(jsonPath("$.instrumentosPorTipo.ACORDO_PARCERIA").value(1))
                .andExpect(jsonPath("$.instrumentosPorTipo.ACORDO_COOPERACAO_TECNICA").value(0))
                .andExpect(jsonPath("$.formalizacoesMensais.2026-05").value(1))
                .andExpect(jsonPath("$.formalizacoesMensais.2026-06").value(1))
                .andExpect(jsonPath("$.formalizacoesMensais.2026-07").value(1))
                .andExpect(jsonPath("$.conclusoesMensais.2026-07").value(1))
                .andExpect(jsonPath("$.conclusoesMensais.2026-08").doesNotExist());

        mockMvc.perform(get("/api/v1/dashboard")
                        .queryParam("origem", "DIPAC")
                        .queryParam("tipo", "CONVENIO")
                        .queryParam("status", "EM_VIGENCIA")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processosPorStatus.EM_FORMALIZACAO").value(0))
                .andExpect(jsonPath("$.processosPorStatus.EM_VIGENCIA").value(1))
                .andExpect(jsonPath("$.processosPorStatus.CONCLUIDO").value(0))
                .andExpect(jsonPath("$.percentualConcluidos").value(0.0))
                .andExpect(jsonPath("$.alertasContratuais").value(0))
                .andExpect(jsonPath("$.alertasTed").value(1))
                .andExpect(jsonPath("$.valorTotalVigente").value(1_500.0))
                .andExpect(jsonPath("$.instrumentosPorTipo.CONTRATO_GESTAO").value(0))
                .andExpect(jsonPath("$.instrumentosPorTipo.CONVENIO").value(1))
                .andExpect(jsonPath("$.instrumentosPorTipo.ACORDO_PARCERIA").value(0))
                .andExpect(jsonPath("$.instrumentosPorTipo.ACORDO_COOPERACAO_TECNICA").value(0))
                .andExpect(jsonPath("$.formalizacoesMensais.2026-05").value(1))
                .andExpect(jsonPath("$.formalizacoesMensais.2026-06").doesNotExist())
                .andExpect(jsonPath("$.conclusoesMensais").isEmpty());
    }

    private long criarProcesso(String token, String numero, String origem) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "numero", numero,
                                "origem", origem))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private long formalizar(
            String token, long processoId, String tipo, int valor,
            LocalDate vigenciaContratual, LocalDate vigenciaTed, LocalDate dataFormalizacao)
            throws Exception {
        long documentoId = criarDocumento(token, "PROCESSO", processoId,
                "instrumento-" + processoId + ".pdf", "Instrumento assinado");
        ObjectNode request = objectMapper.createObjectNode();
        request.put("numero", "IC-" + processoId + "/2026");
        request.put("tipo", tipo);
        request.put("objeto", "Cooperacao institucional");
        request.put("descricao", "Instrumento do portfolio contratual");
        request.put("natureza", "Administrativa");
        request.put("coordenador", "Maria Silva");
        request.putArray("participes").add("UFGD").add("Fundacao");
        request.put("valorAtual", valor);
        request.put("vigenciaContratualFinal", vigenciaContratual.toString());
        if (vigenciaTed != null) request.put("vigenciaTedFinal", vigenciaTed.toString());
        request.put("dataFormalizacao", dataFormalizacao.toString());
        request.put("documentoAssinadoId", documentoId);

        MvcResult resultado = mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private void efetivarTermo(
            String token, long instrumentoId, int valorAnterior, int valorNovo,
            LocalDate vigenciaAnterior, LocalDate vigenciaNova) throws Exception {
        MvcResult criado = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-016/2026",
                                "operacao", "ORIGINAL",
                                "mudancas", List.of(
                                        Map.of(
                                                "campo", "VALOR_ATUAL",
                                                "valorAnterior", valorAnterior + ".00",
                                                "valorNovo", valorNovo + ".00"),
                                        Map.of(
                                                "campo", "VIGENCIA_CONTRATUAL_FINAL",
                                                "valorAnterior", vigenciaAnterior.toString(),
                                                "valorNovo", vigenciaNova.toString()))))))
                .andExpect(status().isCreated())
                .andReturn();
        long termoId = json(criado).get("id").asLong();
        long documentoId = criarDocumento(token, "TERMO_ADITIVO", termoId,
                "termo-016.pdf", "Termo aditivo assinado");

        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", termoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEfetivacao", HOJE.minusDays(1).toString(),
                                "ordemOficial", 1,
                                "documentoAssinadoId", documentoId))))
                .andExpect(status().isOk());
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
            return Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
