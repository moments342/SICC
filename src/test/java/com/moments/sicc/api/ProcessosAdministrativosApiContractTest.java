package com.moments.sicc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.repository.RegistroAuditoriaRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-processos;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProcessosAdministrativosApiContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RegistroAuditoriaRepository auditoria;

    @Test
    void operadorAtribuiResponsavelDipacAtivoAoCadastrarProcesso() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        MvcResult operadorCriado = mockMvc.perform(post("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenAdmin))
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
                .andExpect(status().isCreated())
                .andReturn();
        long operadorId = json(operadorCriado).get("id").asLong();
        String tokenOperador = trocarSenhaTemporaria(
                "operador", "Operador123!", "Operador456!");

        mockMvc.perform(get("/api/v1/processos/responsaveis")
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Administrador de Teste"))
                .andExpect(jsonPath("$[0].perfil").value("ADMINISTRADOR_DIPAC"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].login").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(operadorId))
                .andExpect(jsonPath("$[1].nome").value("Operador DIPAC"))
                .andExpect(jsonPath("$[1].perfil").value("OPERADOR_DIPAC"));

        MvcResult processoCriado = mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(tokenOperador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "numero":"23005.000006/2026-10",
                                  "origem":"DIPAC",
                                  "numeroProjeto":"P-006",
                                  "responsavelId":%d
                                }
                                """.formatted(operadorId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EM_FORMALIZACAO"))
                .andExpect(jsonPath("$.responsavel.id").value(operadorId))
                .andReturn();
        long processoId = json(processoCriado).get("id").asLong();

        assertThat(auditoria.findByEntidadeAndEntidadeIdOrderByCriadoEmDesc(
                "PROCESSO_ADMINISTRATIVO", processoId))
                .singleElement()
                .satisfies(registro -> {
                    assertThat(registro.getAcao()).isEqualTo("CRIAR_PROCESSO");
                    assertThat(registro.getUsuario().getId()).isEqualTo(operadorId);
                });
    }

    @Test
    void usuarioDesativadoNaoApareceNemPodeSerAtribuidoComoResponsavel() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        MvcResult operadorCriado = mockMvc.perform(post("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Operador Inativo",
                                  "email":"inativo@ufgd.edu.br",
                                  "login":"inativo",
                                  "senhaTemporaria":"Operador123!",
                                  "perfil":"OPERADOR_DIPAC"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long operadorId = json(operadorCriado).get("id").asLong();

        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/ativo", operadorId)
                        .queryParam("ativo", "false")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/processos/responsaveis")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(operadorId)).isEmpty());

        mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "numero":"23005.000063/2026-10",
                                  "origem":"DIPAC",
                                  "responsavelId":%d
                                }
                                """.formatted(operadorId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("O usuário responsável está inativo."));
    }

    @Test
    void contratoHttpValidaCamposNormalizaNumeroEImpedeStatusManualOuDuplicado() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();

        mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numero":"","origem":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("numero")))
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("origem")));
        mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "numero", "N".repeat(61),
                                "origem", "O".repeat(151),
                                "numeroProjeto", "P".repeat(81)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("numero")))
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("origem")))
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("numeroProjeto")));

        mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "numero":"PA-STATUS-MANUAL-006",
                                  "origem":"Faculdade de Administração",
                                  "status":"CONCLUIDO"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem")
                        .value(org.hamcrest.Matchers.containsString(
                                "status não deve ser informado manualmente")));

        mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "numero":"  pa-006/2026  ",
                                  "origem":"  Faculdade de Administração  ",
                                  "numeroProjeto":"  Projeto 006  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value("PA-006/2026"))
                .andExpect(jsonPath("$.origem").value("Faculdade de Administração"))
                .andExpect(jsonPath("$.numeroProjeto").value("Projeto 006"))
                .andExpect(jsonPath("$.status").value("EM_FORMALIZACAO"));

        mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numero":" pa-006/2026 ","origem":"Outra origem"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Já existe Processo Administrativo com este número."));
    }

    @Test
    void persistenciaDecideUnicidadeQuandoCadastrosDoMesmoNumeroConcorrem() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        CountDownLatch inicio = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> statusCriacaoConcorrente(
                    inicio, tokenAdmin, "PROC-CONCORRENTE-006"));
            var segunda = executor.submit(() -> statusCriacaoConcorrente(
                    inicio, tokenAdmin, " proc-concorrente-006 "));
            inicio.countDown();

            assertThat(java.util.List.of(primeira.get(), segunda.get()))
                    .containsExactlyInAnyOrder(201, 422);
        }

        mockMvc.perform(get("/api/v1/processos")
                        .queryParam("numero", "PROC-CONCORRENTE-006")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void processosAtivosSaoCorrigidosFiltradosEDesativadosSemSairDaConsultaPublica() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        long primeiroId = criarProcesso(
                tokenAdmin, "23005.000061/2026-10", "Faculdade de Administração", "P-061")
                .get("id").asLong();
        criarProcesso(tokenAdmin, "23005.000062/2026-10", "Pró-Reitoria", "P-062");

        mockMvc.perform(get("/api/v1/processos")
                        .queryParam("numero", "000061")
                        .queryParam("origem", "administração")
                        .queryParam("status", "EM_FORMALIZACAO")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(primeiroId));

        mockMvc.perform(put("/api/v1/processos/{id}", primeiroId)
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "origem":" Faculdade de Ciências Humanas ",
                                  "numeroProjeto":null,
                                  "responsavelId":null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("23005.000061/2026-10"))
                .andExpect(jsonPath("$.origem").value("Faculdade de Ciências Humanas"))
                .andExpect(jsonPath("$.numeroProjeto").isEmpty())
                .andExpect(jsonPath("$.status").value("EM_FORMALIZACAO"));

        MvcResult consultaPublica = mockMvc.perform(get("/api/v1/public/processos")
                        .queryParam("numero", "23005.000061/2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipoInstrumento")
                        .value("Ainda não formalizado"))
                .andExpect(jsonPath("$.content[0].coordenador")
                        .value("Ainda não formalizado"))
                .andExpect(jsonPath("$.content[0].origem")
                        .value("Faculdade de Ciências Humanas"))
                .andReturn();
        JsonNode itemPublico = json(consultaPublica).get("content").get(0);
        assertThat(itemPublico.properties())
                .extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder(
                        "numeroProcesso",
                        "tipoInstrumento",
                        "origem",
                        "coordenador",
                        "status",
                        "vigenciaContratualFinal",
                        "vigenciaTedFinal");
        assertThat(itemPublico.get("vigenciaContratualFinal").isNull()).isTrue();
        assertThat(itemPublico.get("vigenciaTedFinal").isNull()).isTrue();

        mockMvc.perform(delete("/api/v1/processos/{id}", primeiroId)
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/public/processos")
                        .queryParam("numero", "23005.000061/2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        assertThat(auditoria.findByEntidadeAndEntidadeIdOrderByCriadoEmDesc(
                "PROCESSO_ADMINISTRATIVO", primeiroId))
                .extracting(registro -> registro.getAcao())
                .containsExactlyInAnyOrder(
                        "CRIAR_PROCESSO", "ALTERAR_PROCESSO", "DESATIVAR_PROCESSO");
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

    private String trocarSenhaTemporaria(String login, String senhaTemporaria, String senhaPermanente)
            throws Exception {
        String tokenTemporario = tokenDoLogin(login, senhaTemporaria, true);
        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", bearer(tokenTemporario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"%s","novaSenha":"%s"}
                                """.formatted(senhaTemporaria, senhaPermanente)))
                .andExpect(status().isNoContent());
        return tokenDoLogin(login, senhaPermanente, false);
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

    private int statusCriacaoConcorrente(CountDownLatch inicio, String token, String numero) {
        try {
            inicio.await();
            return mockMvc.perform(post("/api/v1/processos")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "numero", numero,
                                    "origem", "DIPAC"))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception e) {
            return 500;
        }
    }

    private JsonNode criarProcesso(String token, String numero, String origem, String numeroProjeto)
            throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "numero", numero,
                                "origem", origem,
                                "numeroProjeto", numeroProjeto))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
