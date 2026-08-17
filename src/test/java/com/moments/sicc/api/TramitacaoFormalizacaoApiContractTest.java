package com.moments.sicc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-tramitacao;MODE=PostgreSQL")
@AutoConfigureMockMvc
@Import(TramitacaoFormalizacaoApiContractTest.RelogioFixoConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TramitacaoFormalizacaoApiContractTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 7, 30);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void historicoOrdenaDatasEEmpatesEDerivaSetorAtualComRelogioInjetado() throws Exception {
        String token = tokenAdministradorPermanente();
        long dipacId = criarSetor(token, "DIPAC", "Divisão de Parcerias");
        long proapId = criarSetor(token, "PROAP", "Pró-Reitoria de Administração");
        long processoId = criarProcesso(token, "PROC-TRAM-007");

        movimentar(token, processoId, HOJE, dipacId, "Entrada na DIPAC")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenciaDiaria").value(1));
        movimentar(token, processoId, HOJE, proapId, "Envio à PROAP")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenciaDiaria").value(2));
        movimentar(token, processoId, HOJE.minusDays(2), dipacId, "Registro histórico")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenciaDiaria").value(1));
        movimentar(token, processoId, HOJE.plusDays(1), dipacId, "Data futura")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("A data da movimentação não pode ser futura."));

        mockMvc.perform(get("/api/v1/processos/{id}/tramitacao", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setorAtual.sigla").value("PROAP"))
                .andExpect(jsonPath("$.movimentacoes.length()").value(3))
                .andExpect(jsonPath("$.movimentacoes[0].dataMovimentacao")
                        .value(HOJE.minusDays(2).toString()))
                .andExpect(jsonPath("$.movimentacoes[0].sequenciaDiaria").value(1))
                .andExpect(jsonPath("$.movimentacoes[1].dataMovimentacao").value(HOJE.toString()))
                .andExpect(jsonPath("$.movimentacoes[1].sequenciaDiaria").value(1))
                .andExpect(jsonPath("$.movimentacoes[2].dataMovimentacao").value(HOJE.toString()))
                .andExpect(jsonPath("$.movimentacoes[2].sequenciaDiaria").value(2));
    }

    @Test
    void permanenciaContaChegadasASetoresDiferentesEMantemPeriodoAbertoAteHoje() throws Exception {
        String token = tokenAdministradorPermanente();
        long dipacId = criarSetor(token, "DIPAC", "Divisão de Parcerias");
        long proapId = criarSetor(token, "PROAP", "Pró-Reitoria de Administração");
        long processoId = criarProcesso(token, "PROC-PERM-007");

        movimentar(token, processoId, HOJE.minusDays(10), dipacId, "Chegada")
                .andExpect(status().isCreated());
        movimentar(token, processoId, HOJE.minusDays(8), dipacId, "Análise interna")
                .andExpect(status().isCreated());
        movimentar(token, processoId, HOJE.minusDays(5), proapId, "Envio à PROAP")
                .andExpect(status().isCreated());
        movimentar(token, processoId, HOJE.minusDays(3), proapId, "Complementação")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/processos/{id}/tramitacao", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permanencias.length()").value(2))
                .andExpect(jsonPath("$.permanencias[0].setor.sigla").value("DIPAC"))
                .andExpect(jsonPath("$.permanencias[0].dataChegada")
                        .value(HOJE.minusDays(10).toString()))
                .andExpect(jsonPath("$.permanencias[0].dataSaida")
                        .value(HOJE.minusDays(5).toString()))
                .andExpect(jsonPath("$.permanencias[0].diasCorridos").value(5))
                .andExpect(jsonPath("$.permanencias[0].aberta").value(false))
                .andExpect(jsonPath("$.permanencias[1].setor.sigla").value("PROAP"))
                .andExpect(jsonPath("$.permanencias[1].dataChegada")
                        .value(HOJE.minusDays(5).toString()))
                .andExpect(jsonPath("$.permanencias[1].dataSaida").isEmpty())
                .andExpect(jsonPath("$.permanencias[1].diasCorridos").value(5))
                .andExpect(jsonPath("$.permanencias[1].aberta").value(true));
    }

    @Test
    void movimentoSalvoNaoPodeSerEditadoECorrecaoViraNovoRegistroAuditado() throws Exception {
        String token = tokenAdministradorPermanente();
        long dipacId = criarSetor(token, "DIPAC", "Divisão de Parcerias");
        long processoId = criarProcesso(token, "PROC-IMUT-007");

        mockMvc.perform(post("/api/v1/movimentacoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "contextoTipo", "FORMALIZACAO",
                                "contextoId", processoId,
                                "dataMovimentacao", HOJE.minusDays(1),
                                "setorDestinoId", dipacId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem")
                        .value(org.hamcrest.Matchers.containsString("observacao")));

        MvcResult original = movimentar(
                token, processoId, HOJE.minusDays(1), dipacId, "Entrada original")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autor.login").value("admin"))
                .andExpect(jsonPath("$.inseridoEm").exists())
                .andReturn();
        long movimentoId = json(original).get("id").asLong();

        mockMvc.perform(put("/api/v1/movimentacoes/{id}", movimentoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "dataMovimentacao", HOJE,
                                "setorDestinoId", dipacId,
                                "observacao", "Sobrescrita indevida"))))
                .andExpect(status().isNotFound());

        MvcResult correcao = movimentar(
                token, processoId, HOJE.minusDays(1), dipacId,
                "Correção: considerar a segunda conferência documental")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenciaDiaria").value(2))
                .andReturn();
        long correcaoId = json(correcao).get("id").asLong();

        mockMvc.perform(get("/api/v1/processos/{id}/tramitacao", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movimentacoes.length()").value(2))
                .andExpect(jsonPath("$.movimentacoes[0].id").value(movimentoId))
                .andExpect(jsonPath("$.movimentacoes[0].observacao").value("Entrada original"))
                .andExpect(jsonPath("$.movimentacoes[1].id").value(correcaoId))
                .andExpect(jsonPath("$.movimentacoes[1].observacao")
                        .value("Correção: considerar a segunda conferência documental"));

        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "CRIAR_MOVIMENTACAO")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].ator.login").value("admin"))
                .andExpect(jsonPath("$.content[0].objeto.tipo").value("MOVIMENTACAO"))
                .andExpect(jsonPath("$.content[0].detalhes")
                        .value(org.hamcrest.Matchers.containsString("sequenciaDiaria=2")))
                .andExpect(jsonPath("$.content[1].ator.login").value("admin"))
                .andExpect(jsonPath("$.content[1].objeto.tipo").value("MOVIMENTACAO"))
                .andExpect(jsonPath("$.content[1].detalhes")
                        .value(org.hamcrest.Matchers.containsString("sequenciaDiaria=1")));
    }

    @Test
    void movimentacoesConcorrentesRecebemSequenciaDiariaAutomaticaSemConflitos() throws Exception {
        String token = tokenAdministradorPermanente();
        long dipacId = criarSetor(token, "DIPAC", "Divisão de Parcerias");
        long processoId = criarProcesso(token, "PROC-CONC-007");
        CountDownLatch inicio = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(6)) {
            var resultados = java.util.stream.IntStream.range(0, 6)
                    .mapToObj(indice -> executor.submit(() -> movimentarConcorrentemente(
                            inicio, token, processoId, dipacId, indice)))
                    .toList();
            inicio.countDown();

            var respostas = resultados.stream().map(futuro -> {
                try {
                    return futuro.get();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }).toList();
            assertThat(respostas)
                    .extracting(RespostaConcorrente::status)
                    .containsOnly(201);
            assertThat(respostas)
                    .extracting(RespostaConcorrente::sequencia)
                    .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
        }

        mockMvc.perform(get("/api/v1/processos/{id}/tramitacao", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movimentacoes.length()").value(6));
    }

    private long criarSetor(String token, String sigla, String nome) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/admin/setores")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("sigla", sigla, "nome", nome))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
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

    private org.springframework.test.web.servlet.ResultActions movimentar(
            String token,
            long processoId,
            LocalDate data,
            long setorId,
            String observacao) throws Exception {
        return mockMvc.perform(post("/api/v1/movimentacoes")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "contextoTipo", "FORMALIZACAO",
                        "contextoId", processoId,
                        "dataMovimentacao", data,
                        "setorDestinoId", setorId,
                        "observacao", observacao))));
    }

    private RespostaConcorrente movimentarConcorrentemente(
            CountDownLatch inicio,
            String token,
            long processoId,
            long setorId,
            int indice) {
        try {
            inicio.await();
            MvcResult resultado = movimentar(
                    token, processoId, HOJE, setorId, "Movimento " + indice)
                    .andReturn();
            JsonNode resposta = json(resultado);
            return new RespostaConcorrente(
                    resultado.getResponse().getStatus(),
                    resposta.has("sequenciaDiaria") ? resposta.get("sequenciaDiaria").asInt() : null);
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

    private String tokenDoLogin(String login, String senha, boolean trocaObrigatoria) throws Exception {
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

    private record RespostaConcorrente(int status, Integer sequencia) {}

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
