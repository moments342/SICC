package com.moments.sicc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.repository.RegistroAuditoriaRepository;
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
        "spring.datasource.url=jdbc:h2:mem:sicc-auth-contract;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AutenticacaoApiContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RegistroAuditoriaRepository auditoria;

    @Test
    void rotaInternaSemJwtDevolveErroHttpPadronizado() throws Exception {
        mockMvc.perform(get("/api/v1/processos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Unauthorized"))
                .andExpect(jsonPath("$.mensagem").value("Autenticação necessária."))
                .andExpect(jsonPath("$.instante").exists());
    }

    @Test
    void tokenInvalidoDevolveErroHttpPadronizado() throws Exception {
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Unauthorized"))
                .andExpect(jsonPath("$.mensagem").value("Token inválido ou expirado."))
                .andExpect(jsonPath("$.instante").exists());
    }

    @Test
    void payloadDeLoginMalformadoDevolveErroHttpPadronizado() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Bad Request"))
                .andExpect(jsonPath("$.mensagem").value("Requisição JSON inválida."))
                .andExpect(jsonPath("$.instante").exists());
    }

    @Test
    void loginEObrigatoriedadeDeTrocaSaoAuditadosSemExporSegredos() throws Exception {
        String senhaInvalida = "NaoRegistrar987!";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"desconhecido","senha":"%s"}
                                """.formatted(senhaInvalida)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Unauthorized"))
                .andExpect(jsonPath("$.mensagem").value("Credenciais inválidas."));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"admin","senha":"Temporaria123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ADMINISTRADOR_DIPAC"))
                .andExpect(jsonPath("$.trocaSenhaObrigatoria").value(true))
                .andReturn();
        JsonNode resposta = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        String token = resposta.get("token").asText();
        assertThat(token.split("\\.")).hasSize(3);

        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.erro").value("Forbidden"))
                .andExpect(jsonPath("$.mensagem").value("Troca de senha obrigatória."))
                .andExpect(jsonPath("$.instante").exists());

        var logins = auditoria.findAll().stream()
                .filter(registro -> registro.getAcao().equals("LOGIN"))
                .toList();
        assertThat(logins)
                .extracting(registro -> registro.isSucesso())
                .containsExactlyInAnyOrder(false, true);
        assertThat(logins).allSatisfy(registro -> {
            assertThat(registro.getDetalhes()).doesNotContain(senhaInvalida);
            assertThat(registro.getDetalhes()).doesNotContain("Temporaria123!");
            assertThat(registro.getDetalhes()).doesNotContain(token);
        });
    }

    @Test
    void administradorTrocaSenhaTemporariaAntesDeAcessarAreaInterna() throws Exception {
        String tokenTemporario = tokenDoLogin("admin", "Temporaria123!", true);

        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", "Bearer " + tokenTemporario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Temporaria123!","novaSenha":"Permanente123!"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", "Bearer " + tokenTemporario))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Token inválido ou expirado."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"admin","senha":"Temporaria123!"}
                                """))
                .andExpect(status().isUnauthorized());

        String tokenPermanente = tokenDoLogin("admin", "Permanente123!", false);
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", "Bearer " + tokenPermanente))
                .andExpect(status().isOk());
    }

    @Test
    void trocaDeSenhaRejeitaSenhaAtualIncorretaENovaSenhaFracaComErrosPadronizados() throws Exception {
        String tokenTemporario = tokenDoLogin("admin", "Temporaria123!", true);

        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", "Bearer " + tokenTemporario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Incorreta123!","novaSenha":"Permanente123!"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.erro").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.mensagem").value("Senha atual inválida."))
                .andExpect(jsonPath("$.instante").exists());

        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", "Bearer " + tokenTemporario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Temporaria123!","novaSenha":"fraca"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.erro").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.mensagem")
                        .value("A senha deve ter ao menos 10 caracteres, com maiúscula, minúscula e número."))
                .andExpect(jsonPath("$.instante").exists());
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
        return objectMapper.readTree(resultado.getResponse().getContentAsByteArray()).get("token").asText();
    }
}
