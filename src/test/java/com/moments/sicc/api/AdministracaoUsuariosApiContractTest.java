package com.moments.sicc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        "spring.datasource.url=jdbc:h2:mem:sicc-admin-usuarios;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdministracaoUsuariosApiContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RegistroAuditoriaRepository auditoria;

    @Test
    void administradorCriaListaEDetalhaUsuarioSemExporSegredos() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();

        MvcResult criado = mockMvc.perform(post("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Operador DIPAC",
                                  "email":"Operador@UFGD.edu.br",
                                  "login":"Operador.Novo",
                                  "senhaTemporaria":"Operador123!",
                                  "perfil":"OPERADOR_DIPAC"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Operador DIPAC"))
                .andExpect(jsonPath("$.email").value("operador@ufgd.edu.br"))
                .andExpect(jsonPath("$.login").value("operador.novo"))
                .andExpect(jsonPath("$.perfil").value("OPERADOR_DIPAC"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.senhaTemporaria").value(true))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.versaoAcesso").doesNotExist())
                .andReturn();
        long usuarioId = json(criado).get("id").asLong();

        mockMvc.perform(get("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %s)].login".formatted(usuarioId))
                        .value("operador.novo"))
                .andExpect(jsonPath("$..senhaHash").doesNotExist())
                .andExpect(jsonPath("$..versaoAcesso").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/usuarios/{id}", usuarioId)
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuarioId))
                .andExpect(jsonPath("$.login").value("operador.novo"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.versaoAcesso").doesNotExist());

        assertThat(auditoria.findAll())
                .anySatisfy(registro -> {
                    assertThat(registro.getAcao()).isEqualTo("CRIAR_USUARIO");
                    assertThat(registro.getEntidadeId()).isEqualTo(usuarioId);
                    assertThat(registro.getDetalhes()).isNullOrEmpty();
                });
    }

    @Test
    void operadorNaoAcessaNenhumaOperacaoDeAdministracaoDeUsuarios() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        long operadorId = criarUsuario(tokenAdmin, "operador", "operador@ufgd.edu.br", "Operador123!",
                "OPERADOR_DIPAC");
        String tokenTemporario = tokenDoLogin("operador", "Operador123!", true);
        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", bearer(tokenTemporario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Operador123!","novaSenha":"Operador456!"}
                                """))
                .andExpect(status().isNoContent());
        String tokenOperador = tokenDoLogin("operador", "Operador456!", false);

        mockMvc.perform(get("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/usuarios/{id}", operadorId)
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenOperador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Sem Permissão",
                                  "email":"sem-permissao@ufgd.edu.br",
                                  "login":"sem-permissao",
                                  "senhaTemporaria":"Operador789!",
                                  "perfil":"OPERADOR_DIPAC"
                                }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/senha", operadorId)
                        .header("Authorization", bearer(tokenOperador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"novaSenhaTemporaria":"Operador789!"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/ativo", operadorId)
                        .queryParam("ativo", "false")
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/perfil", operadorId)
                        .queryParam("perfil", "ADMINISTRADOR_DIPAC")
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isForbidden());
    }

    @Test
    void redefinicaoDeSenhaInvalidaTokenAnteriorEVoltaAExigirTroca() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        long operadorId = criarUsuario(tokenAdmin, "operador", "operador@ufgd.edu.br", "Operador123!",
                "OPERADOR_DIPAC");
        String tokenAnterior = tokenDoLogin("operador", "Operador123!", true);

        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/senha", operadorId)
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"novaSenhaTemporaria":"Operador456!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senhaTemporaria").value(true));

        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", bearer(tokenAnterior))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Operador456!","novaSenha":"Operador789!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Token inválido ou expirado."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"operador","senha":"Operador123!"}
                                """))
                .andExpect(status().isUnauthorized());

        String novoTokenTemporario = tokenDoLogin("operador", "Operador456!", true);
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", bearer(novoTokenTemporario)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("Troca de senha obrigatória."));
        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", bearer(novoTokenTemporario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Operador456!","novaSenha":"Operador789!"}
                                """))
                .andExpect(status().isNoContent());
        tokenDoLogin("operador", "Operador789!", false);

        assertThat(auditoria.findAll())
                .anySatisfy(registro -> {
                    assertThat(registro.getAcao()).isEqualTo("REDEFINIR_SENHA");
                    assertThat(registro.getEntidadeId()).isEqualTo(operadorId);
                    assertThat(registro.getDetalhes()).isNullOrEmpty();
                });
    }

    @Test
    void desativacaoBloqueiaAcessoPreservaAutoriaEReativacaoExigeNovoLogin() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        long operadorId = criarUsuario(tokenAdmin, "operador", "operador@ufgd.edu.br", "Operador123!",
                "OPERADOR_DIPAC");
        String tokenOperador = trocarSenhaTemporaria(
                "operador", "Operador123!", "Operador456!");

        mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(tokenOperador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "numero":"23005.000003/2026-10",
                                  "origem":"DIPAC",
                                  "numeroProjeto":"P-003"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/ativo", operadorId)
                        .queryParam("ativo", "false")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"operador","senha":"Operador456!"}
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/usuarios/{id}", operadorId)
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        assertThat(auditoria.findAll())
                .anySatisfy(registro -> {
                    assertThat(registro.getAcao()).isEqualTo("CRIAR_PROCESSO");
                    assertThat(registro.getUsuario().getId()).isEqualTo(operadorId);
                });

        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/ativo", operadorId)
                        .queryParam("ativo", "true")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isUnauthorized());

        String novoToken = tokenDoLogin("operador", "Operador456!", false);
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", bearer(novoToken)))
                .andExpect(status().isOk());

        assertThat(auditoria.findAll())
                .extracting(registro -> registro.getAcao())
                .contains("DESATIVAR_USUARIO", "REATIVAR_USUARIO");
    }

    @Test
    void alteracaoDePerfilInvalidaSessoesEAdministradorNaoRebaixaAPropriaConta() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();

        mockMvc.perform(patch("/api/v1/admin/usuarios/1/perfil")
                        .queryParam("perfil", "OPERADOR_DIPAC")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("O administrador não pode alterar o próprio perfil."));
        mockMvc.perform(patch("/api/v1/admin/usuarios/1/ativo")
                        .queryParam("ativo", "false")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("O administrador não pode desativar a própria conta."));
        mockMvc.perform(get("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk());

        long operadorId = criarUsuario(tokenAdmin, "operador", "operador@ufgd.edu.br", "Operador123!",
                "OPERADOR_DIPAC");
        String tokenOperadorAntigo = trocarSenhaTemporaria(
                "operador", "Operador123!", "Operador456!");

        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/perfil", operadorId)
                        .queryParam("perfil", "ADMINISTRADOR_DIPAC")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ADMINISTRADOR_DIPAC"));
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", bearer(tokenOperadorAntigo)))
                .andExpect(status().isUnauthorized());

        String tokenNovoAdmin = tokenDoLogin("operador", "Operador456!", false);
        mockMvc.perform(get("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenNovoAdmin)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/perfil", operadorId)
                        .queryParam("perfil", "OPERADOR_DIPAC")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("OPERADOR_DIPAC"));
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", bearer(tokenNovoAdmin)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", bearer(tokenOperadorAntigo)))
                .andExpect(status().isUnauthorized());

        String tokenNovoOperador = tokenDoLogin("operador", "Operador456!", false);
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", bearer(tokenNovoOperador)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenNovoOperador)))
                .andExpect(status().isForbidden());

        assertThat(auditoria.findAll().stream()
                .filter(registro -> registro.getAcao().equals("ALTERAR_PERFIL")
                        && registro.getEntidadeId().equals(operadorId)))
                .hasSize(2);
    }

    @Test
    void cadastrosConcorrentesPreservamUnicidadeDeLoginEEmail() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        String payload = """
                {
                  "nome":"Operador Concorrente",
                  "email":"concorrente@ufgd.edu.br",
                  "login":"concorrente",
                  "senhaTemporaria":"Operador123!",
                  "perfil":"OPERADOR_DIPAC"
                }
                """;
        CountDownLatch inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var requisicao = (java.util.concurrent.Callable<Integer>) () -> {
                inicio.await();
                try {
                    return mockMvc.perform(post("/api/v1/admin/usuarios")
                                    .header("Authorization", bearer(tokenAdmin))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    return 500;
                }
            };
            var primeira = executor.submit(requisicao);
            var segunda = executor.submit(requisicao);
            inicio.countDown();

            assertThat(java.util.List.of(primeira.get(), segunda.get()))
                    .containsExactlyInAnyOrder(201, 422);
        }

        mockMvc.perform(post("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload
                                .replace("concorrente@ufgd.edu.br", "CONCORRENTE@UFGD.EDU.BR")
                                .replace("\"concorrente\"", "\"CONCORRENTE\"")))
                .andExpect(status().isUnprocessableEntity());
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

    private long criarUsuario(String tokenAdmin, String login, String email, String senha, String perfil)
            throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Usuário DIPAC",
                                  "email":"%s",
                                  "login":"%s",
                                  "senhaTemporaria":"%s",
                                  "perfil":"%s"
                                }
                                """.formatted(email, login, senha, perfil)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
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

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
