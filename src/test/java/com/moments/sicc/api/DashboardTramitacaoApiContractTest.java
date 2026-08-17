package com.moments.sicc.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-dashboard-tramitacao;MODE=PostgreSQL")
@AutoConfigureMockMvc
@Import(DashboardTramitacaoApiContractTest.RelogioFixoConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DashboardTramitacaoApiContractTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 8, 8);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void painelExpoePermanenciasETempoInicialComSeusProcessosComponentes()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long dipacId = criarSetor(token, "DIPAC", "Divisao de Parcerias");
        long proapId = criarSetor(token, "PROAP", "Pro-Reitoria de Administracao");

        long formalizadoId = criarProcesso(token, "PROC-FORMALIZADO-017");
        definirDataCadastro(formalizadoId, LocalDate.of(2026, 7, 29));
        movimentar(token, formalizadoId, LocalDate.of(2026, 7, 29), dipacId, "Chegada");
        movimentar(token, formalizadoId, LocalDate.of(2026, 7, 31), dipacId, "Analise interna");
        movimentar(token, formalizadoId, LocalDate.of(2026, 8, 3), proapId, "Envio a PROAP");
        movimentar(token, formalizadoId, LocalDate.of(2026, 8, 5), proapId, "Complementacao");
        formalizar(token, formalizadoId, LocalDate.of(2026, 8, 3));

        long abertoId = criarProcesso(token, "PROC-ABERTO-017");
        definirDataCadastro(abertoId, LocalDate.of(2026, 8, 2));
        movimentar(token, abertoId, LocalDate.of(2026, 8, 2), dipacId, "Chegada");
        movimentar(token, abertoId, LocalDate.of(2026, 8, 5), proapId, "Envio a PROAP");

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permanenciaMediaPorSetor.DIPAC").value(4.0))
                .andExpect(jsonPath("$.permanenciaMediaPorSetor.PROAP").value(4.0))
                .andExpect(jsonPath("$.maiorGargalo").value("DIPAC"))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.DIPAC.length()").value(2))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.DIPAC[0].processoId")
                        .value(abertoId))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.DIPAC[0].diasCorridos")
                        .value(3))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.DIPAC[1].processoId")
                        .value(formalizadoId))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.DIPAC[1].numeroProcesso")
                        .value("PROC-FORMALIZADO-017"))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.DIPAC[1].dataChegada")
                        .value("2026-07-29"))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.DIPAC[1].dataSaida")
                        .value("2026-08-03"))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.DIPAC[1].diasCorridos")
                        .value(5))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.DIPAC[1].aberta")
                        .value(false))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.PROAP.length()").value(2))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.PROAP[1].dataChegada")
                        .value("2026-08-03"))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.PROAP[1].dataSaida")
                        .isEmpty())
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.PROAP[1].diasCorridos")
                        .value(5))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.PROAP[1].aberta")
                        .value(true))
                .andExpect(jsonPath("$.tempoMedioTramitacaoInicialDias").value(5.5))
                .andExpect(jsonPath("$.detalhesTempoTramitacaoInicial.length()").value(2))
                .andExpect(jsonPath("$.detalhesTempoTramitacaoInicial[0].numeroProcesso")
                        .value("PROC-ABERTO-017"))
                .andExpect(jsonPath("$.detalhesTempoTramitacaoInicial[0].diasCorridos").value(6))
                .andExpect(jsonPath("$.detalhesTempoTramitacaoInicial[0].aberta").value(true))
                .andExpect(jsonPath("$.detalhesTempoTramitacaoInicial[1].numeroProcesso")
                        .value("PROC-FORMALIZADO-017"))
                .andExpect(jsonPath("$.detalhesTempoTramitacaoInicial[1].diasCorridos").value(5))
                .andExpect(jsonPath("$.detalhesTempoTramitacaoInicial[1].aberta").value(false));
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

    private void definirDataCadastro(long processoId, LocalDate dataCadastro) {
        jdbc.update(
                "update processos_administrativos set data_cadastro = ? where id = ?",
                dataCadastro,
                processoId);
    }

    private void movimentar(
            String token, long processoId, LocalDate data, long setorId, String observacao)
            throws Exception {
        mockMvc.perform(post("/api/v1/movimentacoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contextoTipo", "FORMALIZACAO",
                                "contextoId", processoId,
                                "dataMovimentacao", data,
                                "setorDestinoId", setorId,
                                "observacao", observacao))))
                .andExpect(status().isCreated());
    }

    private void formalizar(String token, long processoId, LocalDate dataFormalizacao)
            throws Exception {
        long documentoId = criarDocumento(token, processoId);
        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("numero", "IC-017/2026"),
                                Map.entry("tipo", "CONVENIO"),
                                Map.entry("objeto", "Cooperacao institucional"),
                                Map.entry("descricao", "Instrumento formalizado"),
                                Map.entry("natureza", "Administrativa"),
                                Map.entry("coordenador", "Maria Silva"),
                                Map.entry("participes", java.util.List.of("UFGD", "Fundacao")),
                                Map.entry("valorAtual", 1_000),
                                Map.entry("vigenciaContratualFinal", "2027-12-31"),
                                Map.entry("dataFormalizacao", dataFormalizacao.toString()),
                                Map.entry("documentoAssinadoId", documentoId)))))
                .andExpect(status().isCreated());
    }

    private long criarDocumento(String token, long processoId) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "instrumento-017.pdf", MediaType.APPLICATION_PDF_VALUE,
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
