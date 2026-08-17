package com.moments.sicc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.repository.RegistroAuditoriaRepository;
import java.time.LocalDate;
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
        "spring.datasource.url=jdbc:h2:mem:sicc-admin-setores;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdministracaoSetoresApiContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RegistroAuditoriaRepository auditoria;

    @Test
    void administradorCadastraIdentidadesPadronizadasSemDuplicarSiglaOuNome() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();

        criarSetor(tokenAdmin, "  prad ", "  Pró-Reitoria   de Administração  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sigla").value("PRAD"))
                .andExpect(jsonPath("$.nome").value("Pró-Reitoria de Administração"))
                .andExpect(jsonPath("$.ativo").value(true));
        MvcResult dipac = criarSetor(
                tokenAdmin, " dipac ", "  Divisão   de Parcerias e Convênios ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sigla").value("DIPAC"))
                .andExpect(jsonPath("$.nome").value("Divisão de Parcerias e Convênios"))
                .andReturn();
        long dipacId = json(dipac).get("id").asLong();

        criarSetor(tokenAdmin, " DiPaC ", "Outro nome")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Já existe setor com esta sigla."));
        criarSetor(tokenAdmin, "DIPRO", " divisão de   parcerias E convênios ")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Já existe setor com este nome."));
        criarSetor(tokenAdmin, "A".repeat(31), "Setor com sigla inválida")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("sigla")))
                .andExpect(jsonPath("$.mensagem").value(containsString("30")));

        mockMvc.perform(get("/api/v1/admin/setores")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sigla").value("DIPAC"))
                .andExpect(jsonPath("$[1].sigla").value("PRAD"));

        assertThat(auditoria.findAll())
                .filteredOn(registro -> registro.getAcao().equals("CRIAR_SETOR"))
                .hasSize(2)
                .anySatisfy(registro -> {
                    assertThat(registro.getEntidade()).isEqualTo("SETOR");
                    assertThat(registro.getEntidadeId()).isEqualTo(dipacId);
                });
    }

    @Test
    void administradorEditaIdentidadeDoSetorSemIntroduzirDuplicidade() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        long dipacId = json(criarSetor(
                tokenAdmin, "DIPAC", "Divisão de Parcerias e Convênios")
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
        criarSetor(tokenAdmin, "PRAD", "Pró-Reitoria de Administração")
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/admin/setores/{id}", dipacId)
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sigla":" dipro ",
                                  "nome":"  Diretoria   de Projetos e Convênios "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sigla").value("DIPRO"))
                .andExpect(jsonPath("$.nome").value("Diretoria de Projetos e Convênios"));

        mockMvc.perform(put("/api/v1/admin/setores/{id}", dipacId)
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sigla":" prad ","nome":"Outro nome"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Já existe setor com esta sigla."));
        mockMvc.perform(put("/api/v1/admin/setores/{id}", dipacId)
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sigla":"OUTRA",
                                  "nome":" pró-reitoria   DE administração "
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Já existe setor com este nome."));

        assertThat(auditoria.findAll())
                .filteredOn(registro -> registro.getAcao().equals("ALTERAR_SETOR"))
                .singleElement()
                .satisfies(registro -> {
                    assertThat(registro.getEntidadeId()).isEqualTo(dipacId);
                    assertThat(registro.getDetalhes())
                            .contains("DIPAC", "DIPRO", "Divisão de Parcerias e Convênios",
                                    "Diretoria de Projetos e Convênios");
                });
    }

    @Test
    void desativacaoRetiraSetorDosNovosDestinosSemApagarHistorico() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        long setorId = json(criarSetor(
                tokenAdmin, "DIPAC", "Divisão de Parcerias e Convênios")
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
        long processoId = json(mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "numero":"23005.000005/2026-10",
                                  "origem":"DIPAC",
                                  "numeroProjeto":"P-005"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
        String movimentacao = objectMapper.writeValueAsString(java.util.Map.of(
                "contextoTipo", "FORMALIZACAO",
                "contextoId", processoId,
                "dataMovimentacao", LocalDate.now().toString(),
                "setorDestinoId", setorId,
                "observacao", "Entrada no catálogo"));
        mockMvc.perform(post("/api/v1/movimentacoes")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movimentacao))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/admin/setores/{id}/ativo", setorId)
                        .queryParam("ativo", "false")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(patch("/api/v1/admin/setores/{id}/ativo", setorId)
                        .queryParam("ativo", "false")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/setores")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/admin/setores")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(setorId))
                .andExpect(jsonPath("$[0].ativo").value(false));
        mockMvc.perform(get("/api/v1/movimentacoes")
                        .queryParam("contextoTipo", "FORMALIZACAO")
                        .queryParam("contextoId", String.valueOf(processoId))
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].setorDestino.id").value(setorId))
                .andExpect(jsonPath("$[0].setorDestino.sigla").value("DIPAC"))
                .andExpect(jsonPath("$[0].setorDestino.ativo").value(false));
        mockMvc.perform(post("/api/v1/movimentacoes")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movimentacao))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("O setor de destino está inativo."));

        assertThat(auditoria.findAll())
                .filteredOn(registro -> registro.getAcao().equals("DESATIVAR_SETOR"))
                .singleElement()
                .satisfies(registro -> {
                    assertThat(registro.getEntidadeId()).isEqualTo(setorId);
                    assertThat(registro.getDetalhes()).contains("ativo: true -> false");
                });
    }

    @Test
    void operadorConsultaSomenteSetoresAtivosSemPoderAdministrarCatalogo() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        long ativoId = json(criarSetor(tokenAdmin, "DIPAC", "Divisão de Parcerias e Convênios")
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        long inativoId = json(criarSetor(tokenAdmin, "PRAD", "Pró-Reitoria de Administração")
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        mockMvc.perform(patch("/api/v1/admin/setores/{id}/ativo", inativoId)
                        .queryParam("ativo", "false")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk());
        criarUsuarioOperador(tokenAdmin);
        String tokenOperador = trocarSenhaTemporaria(
                "operador", "Operador123!", "Operador456!");

        mockMvc.perform(get("/api/v1/setores")
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ativoId))
                .andExpect(jsonPath("$[0].ativo").value(true));
        mockMvc.perform(get("/api/v1/admin/setores")
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isForbidden());
        criarSetor(tokenOperador, "PROAP", "Pró-Reitoria de Avaliação")
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/setores/{id}", ativoId)
                        .header("Authorization", bearer(tokenOperador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sigla":"OUTRA","nome":"Outra unidade"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/setores/{id}/ativo", ativoId)
                        .queryParam("ativo", "false")
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadastrosConcorrentesPreservamUnicidadeDaIdentidadeDoSetor() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        CountDownLatch inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> statusCriacaoConcorrente(
                    inicio, tokenAdmin, "DIPAC", "Divisão de Parcerias"));
            var segunda = executor.submit(() -> statusCriacaoConcorrente(
                    inicio, tokenAdmin, " dipac ", "Diretoria de Projetos"));
            inicio.countDown();

            assertThat(java.util.List.of(primeira.get(), segunda.get()))
                    .containsExactlyInAnyOrder(201, 422);
        }
    }

    private org.springframework.test.web.servlet.ResultActions criarSetor(
            String token, String sigla, String nome) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/setores")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        java.util.Map.of("sigla", sigla, "nome", nome))));
    }

    private int statusCriacaoConcorrente(
            CountDownLatch inicio, String token, String sigla, String nome) {
        try {
            inicio.await();
            return criarSetor(token, sigla, nome).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            return 500;
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

    private void criarUsuarioOperador(String tokenAdmin) throws Exception {
        mockMvc.perform(post("/api/v1/admin/usuarios")
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
                .andExpect(status().isCreated());
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

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
