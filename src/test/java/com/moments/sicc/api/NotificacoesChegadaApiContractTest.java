package com.moments.sicc.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-notificacoes;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificacoesChegadaApiContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void responsavelAtivoRecebeChegadaPodeAbrirProcessoEMarcarComoLida() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        UsuarioCriado responsavel = criarUsuario(
                tokenAdmin, "Responsável DIPAC", "responsavel", "responsavel@sicc.test");
        String tokenResponsavel = trocarSenhaTemporaria(
                responsavel.login(), "Operador123!", "Responsavel123!");
        long setorId = criarSetor(tokenAdmin, "DIPAC", "Divisão de Parcerias e Convênios");
        long processoId = criarProcesso(tokenAdmin, "PROC-NOT-008-1", responsavel.id());

        movimentar(tokenAdmin, processoId, setorId, "Encaminhamento ao responsável");

        mockMvc.perform(get("/api/v1/notificacoes")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        MvcResult caixaDeEntrada = mockMvc.perform(get("/api/v1/notificacoes")
                        .header("Authorization", bearer(tokenResponsavel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("CHEGADA_TRAMITACAO"))
                .andExpect(jsonPath("$[0].processoId").value(processoId))
                .andExpect(jsonPath("$[0].mensagem").value(
                        "O Processo Administrativo PROC-NOT-008-1 chegou ao setor DIPAC."))
                .andExpect(jsonPath("$[0].lida").value(false))
                .andReturn();
        long notificacaoId = json(caixaDeEntrada).get(0).get("id").asLong();

        mockMvc.perform(delete("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(get("/api/v1/notificacoes/{id}/processo", notificacaoId)
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/notificacoes/{id}/processo", notificacaoId)
                        .header("Authorization", bearer(tokenResponsavel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("PROC-NOT-008-1"))
                .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(patch("/api/v1/notificacoes/{id}/lida", notificacaoId)
                        .header("Authorization", bearer(tokenResponsavel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processoId").value(processoId))
                .andExpect(jsonPath("$.lida").value(true));
    }

    @Test
    void responsavelInativoCedeLugarAosUsuariosAtivosSemNotificarOAutor() throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        UsuarioCriado responsavelInativo = criarUsuario(
                tokenAdmin, "Responsável Inativo", "inativo", "inativo@sicc.test");
        trocarSenhaTemporaria(responsavelInativo.login(), "Operador123!", "Inativo123!");
        UsuarioCriado operadorAtivo = criarUsuario(
                tokenAdmin, "Operador Ativo", "ativo", "ativo@sicc.test");
        String tokenOperadorAtivo = trocarSenhaTemporaria(
                operadorAtivo.login(), "Operador123!", "AtivoSeguro123!");
        UsuarioCriado segundoOperadorAtivo = criarUsuario(
                tokenAdmin, "Segundo Operador Ativo", "ativo2", "ativo2@sicc.test");
        String tokenSegundoOperadorAtivo = trocarSenhaTemporaria(
                segundoOperadorAtivo.login(), "Operador123!", "OutroAtivo123!");
        long setorId = criarSetor(tokenAdmin, "CCOMP", "Coordenadoria de Compras");
        long processoId = criarProcesso(
                tokenAdmin, "PROC-NOT-008-2", responsavelInativo.id());
        definirUsuarioAtivo(tokenAdmin, responsavelInativo.id(), false);

        movimentar(tokenAdmin, processoId, setorId, "Responsável está inativo");

        mockMvc.perform(get("/api/v1/notificacoes")
                        .header("Authorization", bearer(tokenOperadorAtivo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].processoId").value(processoId));
        mockMvc.perform(get("/api/v1/notificacoes")
                        .header("Authorization", bearer(tokenSegundoOperadorAtivo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].processoId").value(processoId));
        mockMvc.perform(get("/api/v1/notificacoes")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        definirUsuarioAtivo(tokenAdmin, responsavelInativo.id(), true);
        String tokenResponsavelReativado = tokenDoLogin(
                responsavelInativo.login(), "Inativo123!", false);
        mockMvc.perform(get("/api/v1/notificacoes")
                        .header("Authorization", bearer(tokenResponsavelReativado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private UsuarioCriado criarUsuario(
            String tokenAdmin, String nome, String login, String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"%s",
                                  "email":"%s",
                                  "login":"%s",
                                  "senhaTemporaria":"Operador123!",
                                  "perfil":"OPERADOR_DIPAC"
                                }
                                """.formatted(nome, email, login)))
                .andExpect(status().isCreated())
                .andReturn();
        return new UsuarioCriado(json(resultado).get("id").asLong(), login);
    }

    private long criarSetor(String token, String sigla, String nome) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/admin/setores")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sigla":"%s","nome":"%s"}
                                """.formatted(sigla, nome)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private void definirUsuarioAtivo(String token, long usuarioId, boolean ativo) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}/ativo", usuarioId)
                        .queryParam("ativo", Boolean.toString(ativo))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(ativo));
    }

    private long criarProcesso(String token, String numero, long responsavelId) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "numero":"%s",
                                  "origem":"DIPAC",
                                  "responsavelId":%d
                                }
                                """.formatted(numero, responsavelId)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private void movimentar(String token, long processoId, long setorId, String observacao)
            throws Exception {
        mockMvc.perform(post("/api/v1/movimentacoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "contextoTipo", "FORMALIZACAO",
                                "contextoId", processoId,
                                "dataMovimentacao", LocalDate.now().toString(),
                                "setorDestinoId", setorId,
                                "observacao", observacao))))
                .andExpect(status().isCreated());
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
        String temporario = tokenDoLogin(login, senhaTemporaria, true);
        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", bearer(temporario))
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

    private record UsuarioCriado(long id, String login) {}
}
