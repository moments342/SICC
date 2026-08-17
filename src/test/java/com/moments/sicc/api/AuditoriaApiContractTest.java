package com.moments.sicc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.domain.RegistroAuditoria;
import com.moments.sicc.repository.RegistroAuditoriaRepository;
import java.time.LocalDate;
import java.util.List;
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
        "spring.datasource.url=jdbc:h2:mem:sicc-auditoria-contract;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuditoriaApiContractTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RegistroAuditoriaRepository registrosAuditoria;

    @Test
    void administradorConsultaFalhasDeLoginPaginadasSemDadosSensiveis() throws Exception {
        String tokenAdministrador = tokenAdministradorPermanente();
        String senhaInvalida = "NaoRegistrar987!";
        registrarFalhaDeLogin("desconhecido", senhaInvalida);
        registrarFalhaDeLogin("outro-desconhecido", senhaInvalida);

        MvcResult resultado = mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "login")
                        .queryParam("resultado", "FALHA")
                        .queryParam("page", "0")
                        .queryParam("size", "1")
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].acao").value("LOGIN"))
                .andExpect(jsonPath("$.content[0].resultado").value("FALHA"))
                .andExpect(jsonPath("$.content[0].ator").value(nullValue()))
                .andExpect(jsonPath("$.content[0].objeto.tipo").value("USUARIO_INTERNO"))
                .andExpect(jsonPath("$.content[0].objeto.id").value(nullValue()))
                .andExpect(jsonPath("$.content[0].criadoEm").exists())
                .andReturn();

        String corpo = resultado.getResponse().getContentAsString();
        assertThat(corpo)
                .doesNotContain(senhaInvalida)
                .doesNotContain(tokenAdministrador)
                .doesNotContainIgnoringCase("senhaHash")
                .doesNotContainIgnoringCase("\"token\"");
    }

    @Test
    void falhaDeLoginComContaConhecidaNaoAtribuiAtorNemPersisteSegredos() throws Exception {
        String tokenAdministrador = tokenAdministradorPermanente();
        String senhaInvalida = "NaoRegistrar987!";
        registrarFalhaDeLogin("admin", senhaInvalida);

        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "LOGIN")
                        .queryParam("resultado", "FALHA")
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ator").value(nullValue()))
                .andExpect(jsonPath("$.content[0].objeto.tipo").value("USUARIO_INTERNO"))
                .andExpect(jsonPath("$.content[0].objeto.id").value(1));

        List<RegistroAuditoria> falhas = registrosAuditoria.findAll().stream()
                .filter(registro -> "LOGIN".equals(registro.getAcao()) && !registro.isSucesso())
                .toList();
        assertThat(falhas).singleElement().satisfies(registro -> {
            assertThat(registro.getUsuario()).isNull();
            assertThat(registro.getEntidadeId()).isEqualTo(1L);
            assertThat(registro.getDetalhes())
                    .doesNotContain(senhaInvalida)
                    .doesNotContain(tokenAdministrador);
        });
    }

    @Test
    void administradorFiltraPorAcaoResultadoUsuarioEPeriodoInclusivo() throws Exception {
        String tokenAdministrador = tokenAdministradorPermanente();
        String hoje = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("acao", "TROCAR_SENHA")
                        .queryParam("resultado", "SUCESSO")
                        .queryParam("usuario", "ADMIN")
                        .queryParam("dataInicial", hoje)
                        .queryParam("dataFinal", hoje)
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ator.id").value(1))
                .andExpect(jsonPath("$.content[0].ator.login").value("admin"))
                .andExpect(jsonPath("$.content[0].ator.nome").value("Administrador de Teste"))
                .andExpect(jsonPath("$.content[0].objeto.tipo").value("USUARIO_INTERNO"))
                .andExpect(jsonPath("$.content[0].objeto.id").value(1));

        String amanha = LocalDate.now().plusDays(1).toString();
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("dataInicial", amanha)
                        .queryParam("dataFinal", amanha)
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void somenteAdministradorAcessaAosRegistrosDeAuditoria() throws Exception {
        String tokenAdministrador = tokenAdministradorPermanente();
        String tokenOperador = tokenOperadorPermanente(tokenAdministrador);

        mockMvc.perform(get("/api/v1/auditoria")
                        .header("Authorization", bearer(tokenOperador)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("Acesso negado."));

        mockMvc.perform(get("/api/v1/auditoria"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Autenticação necessária."));
    }

    @Test
    void registrosNaoPodemSerAlteradosEConsultasComunsNaoGeramRegistros() throws Exception {
        String tokenAdministrador = tokenAdministradorPermanente();
        JsonNode paginaAntes = paginaAuditoria(tokenAdministrador);
        long totalAntes = paginaAntes.get("totalElements").asLong();
        JsonNode conteudoAntes = paginaAntes.get("content");
        long registroId = conteudoAntes.get(0).get("id").asLong();

        mockMvc.perform(put("/api/v1/auditoria/{id}", registroId)
                        .header("Authorization", bearer(tokenAdministrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acao":"ALTERADA"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/auditoria/{id}", registroId)
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/processos")
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/processos"))
                .andExpect(status().isOk());

        JsonNode paginaDepois = paginaAuditoria(tokenAdministrador);
        assertThat(paginaDepois.get("totalElements").asLong()).isEqualTo(totalAntes);
        assertThat(paginaDepois.get("content")).isEqualTo(conteudoAntes);
    }

    @Test
    void consultaRejeitaPeriodoInvertidoETamanhoExcessivo() throws Exception {
        String tokenAdministrador = tokenAdministradorPermanente();

        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("dataInicial", "2026-07-31")
                        .queryParam("dataFinal", "2026-07-30")
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("A data inicial não pode ser posterior à data final."));
        mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("size", "101")
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("O tamanho da página deve estar entre 1 e 100."));
    }

    private void registrarFalhaDeLogin(String login, String senha) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","senha":"%s"}
                                """.formatted(login, senha)))
                .andExpect(status().isUnauthorized());
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

    private String tokenOperadorPermanente(String tokenAdministrador) throws Exception {
        mockMvc.perform(post("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenAdministrador))
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
        String temporario = tokenDoLogin("operador", "Operador123!", true);
        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", bearer(temporario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Operador123!","novaSenha":"Operador456!"}
                                """))
                .andExpect(status().isNoContent());
        return tokenDoLogin("operador", "Operador456!", false);
    }

    private JsonNode paginaAuditoria(String tokenAdministrador) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/auditoria")
                        .queryParam("size", "100")
                        .header("Authorization", bearer(tokenAdministrador)))
                .andExpect(status().isOk())
                .andReturn();
        return json(resultado);
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
