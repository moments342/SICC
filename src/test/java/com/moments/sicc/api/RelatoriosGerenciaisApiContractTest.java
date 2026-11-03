package com.moments.sicc.api;

import static com.moments.sicc.support.ArquivoDocumentoTeste.pdfValido;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.repository.UsuarioInternoRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sicc-gerenciais;MODE=PostgreSQL")
@AutoConfigureMockMvc
@Import(RelatoriosGerenciaisApiContractTest.RelogioConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RelatoriosGerenciaisApiContractTest extends ApiContractTestSupport {
    @Autowired private ProcessoAdministrativoRepository processos;
    @Autowired private UsuarioInternoRepository usuarios;

    @Test
    void consolidadoConciliaComDashboardEAplicaSetorEResponsavelEmTodasAsSecoes() throws Exception {
        String token = tokenAdministradorPermanente();
        Carteira carteira = carteira(token);
        var dashboard = json(mockMvc.perform(get("/api/v1/dashboard").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
        assertThat(dashboard.get("valorTotalVigente").decimalValue()).isEqualByComparingTo("100.00");
        String csv = csv(token, "CONSOLIDADO", Map.of());
        assertThat(csv).contains("total_processos;3;processos", "percentual_concluidos;33.33;%",
                "valor_total_vigente;100.00;R$", "tempo_medio_tramitacao_inicial;80.00;dias", "maior_gargalo;PROAP;setor");
        Map<String, String> filtros = Map.of("setorId", Long.toString(carteira.proap()),
                "responsavelId", Long.toString(carteira.responsavel()));
        String filtrado = csv(token, "CONSOLIDADO", filtros);
        assertThat(filtrado).contains("total_processos;2;processos", "percentual_concluidos;50.00;%",
                "valor_total_vigente;100.00;R$", "tempo_medio_tramitacao_inicial;26.00;dias",
                "PROC-ANTIGO-FORMALIZADO", "PROC-ANTIGO-CONCLUIDO").doesNotContain("PROC-NOVO-ABERTO");
        long id = gerar(token, "CONSOLIDADO", "PDF", filtros);
        try (var pdf = Loader.loadPDF(baixar(token, id))) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Valor total vigente", "100,00", "50%",
                    "PROC-ANTIGO-FORMALIZADO").doesNotContain("PROC-NOVO-ABERTO");
        }
        mockMvc.perform(get("/api/v1/relatorios/filtros").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.setores.length()").value(2))
                .andExpect(jsonPath("$.responsaveis[0].id").value(carteira.responsavel()));
    }

    @Test
    void anualIncluiEventosDeProcessosAntigosERecortaPermanenciasAoAno() throws Exception {
        String token = tokenAdministradorPermanente();
        carteira(token);
        String csv = csv(token, "ANUAL_PROCESSOS", Map.of("ano", "2026"));
        assertThat(csv).contains("cadastros_ano;1;processos", "formalizacoes_ano;1;instrumentos",
                "conclusoes_ano;1;processos", "tempo_medio_formalizacoes_ano;21.00;dias",
                "2026-01;0;1;0", "2026-02;1;0;1", "2026-08;0;0;0",
                "DIPAC;2;4.00", "PROAP;2;143.00", "Maior gargalo do ano: PROAP",
                "PROC-ANTIGO-FORMALIZADO", "PROC-ANTIGO-CONCLUIDO", "PROC-NOVO-ABERTO")
                .doesNotContain("2026-09;", "2026-12;");
        String anterior = csv(token, "ANUAL_PROCESSOS", Map.of("ano", "2025"));
        assertThat(anterior).contains("cadastros_ano;1;processos", "formalizacoes_ano;0;instrumentos",
                "conclusoes_ano;0;processos", "DIPAC;1;2.00").doesNotContain("PROC-NOVO-ABERTO");
    }

    @Test
    void filtrosAusentesValidacaoEVazioSaoExplicitosEAcessoEAutenticado() throws Exception {
        String token = tokenAdministradorPermanente();
        processo("PROC-SEM-ATRIBUICAO", "2026-02-01", null);
        String csv = csv(token, "CONSOLIDADO", Map.of("setorId", "SEM_SETOR", "responsavelId", "SEM_RESPONSAVEL"));
        assertThat(csv).contains("total_processos;1;processos", "PROC-SEM-ATRIBUICAO");
        assertThat(csv(token, "CONSOLIDADO", Map.of("responsavelId", "99999")))
                .contains("total_processos;0;processos", "Nenhum registro encontrado");
        for (var filtro : List.of(Map.of("setorId", "-1"), Map.of("responsavelId", "abc"), Map.of("ano", "0"), Map.of("ano", "2027"))) {
            mockMvc.perform(post("/api/v1/relatorios").header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                            "tipo", "ANUAL_PROCESSOS", "formato", "CSV", "filtros", filtro))))
                    .andExpect(status().isUnprocessableEntity());
        }
        mockMvc.perform(get("/api/v1/relatorios/filtros")).andExpect(status().isUnauthorized());
    }

    @Test
    void operadorFiltraVinculosInativosSemPrecisarAcessarAdministracao() throws Exception {
        String admin = tokenAdministradorPermanente();
        long setor = setor(admin, "HIST", "Setor histórico");
        var usuario = new UsuarioInterno();
        usuario.setNome("Responsável anterior"); usuario.setLogin("anterior"); usuario.setEmail("anterior@sicc.test");
        usuario.setSenhaHash("hash-nao-usado"); usuario.setCriadoEm(LocalDate.of(2025, 1, 1).atStartOfDay());
        usuario.setPerfil(com.moments.sicc.domain.Enums.PerfilAcesso.OPERADOR_DIPAC); usuario.setAtivo(false);
        usuario = usuarios.saveAndFlush(usuario);
        long processo = processo("PROC-VINCULO-INATIVO", "2026-01-01", usuario);
        mover(admin, processo, setor, "2026-08-01");
        mockMvc.perform(patch("/api/v1/admin/setores/{id}/ativo", setor).param("ativo", "false").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/usuarios").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                        "nome", "Operador de relatórios", "login", "relatorios", "email", "relatorios@sicc.test",
                        "perfil", "OPERADOR_DIPAC", "senhaTemporaria", "Temporaria123!"))))
                .andExpect(status().isCreated());
        String temporario = tokenDoLogin("relatorios", "Temporaria123!", true);
        mockMvc.perform(post("/api/v1/auth/senha").header("Authorization", bearer(temporario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"Temporaria123!\",\"novaSenha\":\"Permanente123!\"}"))
                .andExpect(status().isNoContent());
        String operador = tokenDoLogin("relatorios", "Permanente123!", false);
        mockMvc.perform(get("/api/v1/relatorios/filtros").header("Authorization", bearer(operador)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.setores[0].rotulo").value("HIST · Setor histórico (inativo)"))
                .andExpect(jsonPath("$.responsaveis[0].rotulo").value("Responsável anterior (anterior) (inativo)"));
        assertThat(csv(operador, "CONSOLIDADO", Map.of("setorId", Long.toString(setor),
                "responsavelId", usuario.getId().toString())))
                .contains("total_processos;1;processos", "PROC-VINCULO-INATIVO", "Responsável anterior", "HIST");
    }

    private Carteira carteira(String token) throws Exception {
        UsuarioInterno responsavel = usuarios.findAll().getFirst();
        long dipac = setor(token, "DIPAC", "Divisão de Projetos");
        long proap = setor(token, "PROAP", "Pró-Reitoria de Administração");
        long antigo = processo("PROC-ANTIGO-FORMALIZADO", "2025-12-20", responsavel);
        formalizar(token, antigo, "2026-01-10", "2027-01-01", 100);
        mover(token, antigo, dipac, "2025-12-30");
        mover(token, antigo, proap, "2026-01-02");
        long aberto = processo("PROC-NOVO-ABERTO", "2026-02-01", null);
        mover(token, aberto, dipac, "2026-08-01");
        long concluido = processo("PROC-ANTIGO-CONCLUIDO", "2024-01-01", responsavel);
        formalizar(token, concluido, "2024-02-01", "2026-02-01", 900);
        mover(token, concluido, proap, "2026-06-01");
        return new Carteira(proap, responsavel.getId());
    }

    private long processo(String numero, String data, UsuarioInterno responsavel) {
        var p = new ProcessoAdministrativo();
        p.setNumero(numero); p.setOrigem("DIPAC"); p.setDataCadastro(LocalDate.parse(data)); p.setResponsavel(responsavel);
        return processos.saveAndFlush(p).getId();
    }

    private long setor(String token, String sigla, String nome) throws Exception {
        return json(mockMvc.perform(post("/api/v1/admin/setores").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("sigla", sigla, "nome", nome))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private void mover(String token, long processo, long setor, String data) throws Exception {
        mockMvc.perform(post("/api/v1/movimentacoes").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                        "contextoTipo", "FORMALIZACAO", "contextoId", processo, "dataMovimentacao", data,
                        "setorDestinoId", setor, "observacao", "Tramitação de teste"))))
                .andExpect(status().isCreated());
    }

    private void formalizar(String token, long processo, String formalizacao, String vencimento, int valor) throws Exception {
        long documento = json(mockMvc.perform(multipart("/api/v1/documentos")
                .file(new MockMultipartFile("arquivo", "assinado.pdf", MediaType.APPLICATION_PDF_VALUE, pdfValido("Teste")))
                .param("proprietarioTipo", "PROCESSO").param("proprietarioId", Long.toString(processo))
                .param("categoria", "ASSINADO").param("titulo", "Instrumento assinado")
                .header("Authorization", bearer(token))).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        var body = objectMapper.createObjectNode();
        body.put("numero", "CV-" + processo); body.put("tipo", "CONVENIO"); body.put("objeto", "Pesquisa");
        body.put("natureza", "Pesquisa"); body.put("coordenador", "Maria"); body.putArray("participes").add("Universidade");
        body.put("valorAtual", valor); body.put("vigenciaContratualFinal", vencimento);
        body.put("dataFormalizacao", formalizacao); body.put("documentoAssinadoId", documento);
        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processo).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isCreated());
    }

    private String csv(String token, String tipo, Map<String, String> filtros) throws Exception {
        return new String(baixar(token, gerar(token, tipo, "CSV", filtros)), StandardCharsets.UTF_8);
    }

    private long gerar(String token, String tipo, String formato, Map<String, String> filtros) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/relatorios").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                        "tipo", tipo, "formato", formato, "filtros", filtros))))
                .andExpect(status().isCreated()).andReturn();
        return json(resultado).get("id").asLong();
    }

    private byte[] baixar(String token, long id) throws Exception {
        return mockMvc.perform(get("/api/v1/relatorios/{id}/arquivo", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private record Carteira(long proap, long responsavel) {}
    @TestConfiguration static class RelogioConfig {
        @Bean @Primary Clock relogioGerenciais() { return Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC); }
    }
}
