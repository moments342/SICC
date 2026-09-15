package com.moments.sicc.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-notificacoes;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificacoesChegadaApiContractTest extends ApiContractTestSupport {


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

    @Test
    void movimentoRetroativoNoMesmoSetorDoPredecessorNaoRepeteNotificacao()
            throws Exception {
        String tokenAdmin = tokenAdministradorPermanente();
        UsuarioCriado responsavel = criarUsuario(
                tokenAdmin, "Responsável Cronologia", "cronologia", "cronologia@sicc.test");
        String tokenResponsavel = trocarSenhaTemporaria(
                responsavel.login(), "Operador123!", "Cronologia123!");
        long dipacId = criarSetor(tokenAdmin, "DIPAC", "Divisão de Parcerias");
        long proapId = criarSetor(tokenAdmin, "PROAP", "Pró-Reitoria de Administração");
        long processoId = criarProcesso(
                tokenAdmin, "PROC-NOT-CRONOLOGIA-008", responsavel.id());
        LocalDate hoje = LocalDate.now();

        movimentar(tokenAdmin, processoId, hoje.minusDays(10), dipacId, "Chegada DIPAC");
        movimentar(tokenAdmin, processoId, hoje.minusDays(5), proapId, "Chegada PROAP");
        movimentar(tokenAdmin, processoId, hoje.minusDays(8), dipacId, "Registro retroativo");

        mockMvc.perform(get("/api/v1/notificacoes")
                        .header("Authorization", bearer(tokenResponsavel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/processos/{id}/tramitacao", processoId)
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movimentacoes.length()").value(3));
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "CRIAR_MOVIMENTACAO")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
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
        movimentar(token, processoId, LocalDate.now(), setorId, observacao);
    }

    private void movimentar(
            String token, long processoId, LocalDate data, long setorId, String observacao)
            throws Exception {
        mockMvc.perform(post("/api/v1/movimentacoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "contextoTipo", "FORMALIZACAO",
                                "contextoId", processoId,
                                "dataMovimentacao", data.toString(),
                                "setorDestinoId", setorId,
                                "observacao", observacao))))
                .andExpect(status().isCreated());
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

    private record UsuarioCriado(long id, String login) {}
}
