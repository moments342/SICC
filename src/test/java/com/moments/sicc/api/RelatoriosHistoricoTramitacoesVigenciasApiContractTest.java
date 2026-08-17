package com.moments.sicc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.repository.RegistroAuditoriaRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-relatorios-historico-vigencias;MODE=PostgreSQL")
@AutoConfigureMockMvc
@Import(RelatoriosHistoricoTramitacoesVigenciasApiContractTest.RelogioFixoConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RelatoriosHistoricoTramitacoesVigenciasApiContractTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 8, 8);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RegistroAuditoriaRepository auditoria;

    @Test
    void historicoDeTramitacoesFiltradoPreservaPermanenciasEConciliaComDashboardGerencial()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long dipacId = criarSetor(token, "DIPAC", "Divisao de Parcerias");
        long proapId = criarSetor(token, "PROAP", "Pro-Reitoria de Administracao");
        long processoId = criarProcesso(token, "PROC-REL-TRAM-019", "DIPAC");
        long processoAbertoId = criarProcesso(token, "PROC-REL-TRAM-019-ABERTO", "DIPAC");

        movimentar(token, processoId, HOJE.minusDays(10), dipacId, "Chegada");
        movimentar(token, processoId, HOJE.minusDays(8), dipacId, "Analise interna");
        movimentar(token, processoId, HOJE.minusDays(5), proapId, "Envio a PROAP");
        movimentar(token, processoId, HOJE.minusDays(3), proapId, "Complementacao");
        movimentar(token, processoAbertoId, HOJE.minusDays(20), dipacId,
                "Permanencia aberta anterior ao filtro");

        MvcResult gerado = gerar(token, "HISTORICO_TRAMITACOES", "CSV", Map.of(
                "numero", "REL-TRAM-019",
                "contexto", "FORMALIZACAO",
                "dataInicial", HOJE.minusDays(8).toString(),
                "dataFinal", HOJE.minusDays(4).toString()));
        long csvId = json(gerado).get("id").asLong();
        long pdfId = json(gerar(token, "HISTORICO_TRAMITACOES", "PDF", Map.of(
                "numero", "REL-TRAM-019",
                "contexto", "FORMALIZACAO",
                "dataInicial", HOJE.minusDays(8).toString(),
                "dataFinal", HOJE.minusDays(4).toString()))).get("id").asLong();
        long xlsxId = json(gerar(token, "HISTORICO_TRAMITACOES", "XLSX", Map.of(
                "numero", "REL-TRAM-019",
                "contexto", "FORMALIZACAO",
                "dataInicial", HOJE.minusDays(8).toString(),
                "dataFinal", HOJE.minusDays(4).toString()))).get("id").asLong();
        byte[] csvOriginal = baixar(token, csvId);
        String csv = new String(csvOriginal, StandardCharsets.UTF_8);
        String pdf;
        try (var documento = Loader.loadPDF(baixar(token, pdfId))) {
            pdf = new PDFTextStripper().getText(documento);
        }
        String xlsx = conteudoPlanilha(baixar(token, xlsxId));

        assertThat(csv).contains(
                "numero_processo;contexto;contexto_id;data;sequencia;setor_destino;autor;observacao;"
                        + "permanencia_inicio;permanencia_fim;permanencia_dias;permanencia_aberta");
        assertThat(csv).contains(
                "PROC-REL-TRAM-019;FORMALIZACAO;" + processoId + ";2026-07-31;1;DIPAC;"
                        + "Administrador de Teste;Analise interna;;;;");
        assertThat(csv).contains(
                "PROC-REL-TRAM-019;FORMALIZACAO;" + processoId + ";2026-08-03;1;PROAP;"
                        + "Administrador de Teste;Envio a PROAP;2026-08-03;;5;true");
        assertThat(csv).contains(
                "PROC-REL-TRAM-019-ABERTO;FORMALIZACAO;" + processoAbertoId
                        + ";2026-07-19;1;DIPAC;Administrador de Teste;"
                        + "Permanencia aberta anterior ao filtro;2026-07-19;;20;true");
        assertThat(csv).doesNotContain("Chegada").doesNotContain("Complementacao");
        for (String evidencia : List.of(
                "Relatorio do historico de tramitacoes", "PROC-REL-TRAM-019-ABERTO",
                "permanencia_aberta", "20", "true")) {
            assertThat(removerAcentos(csv)).contains(evidencia);
            assertThat(removerAcentos(pdf)).contains(evidencia);
            assertThat(removerAcentos(xlsx)).contains(evidencia);
        }

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.PROAP[0].processoId")
                        .value(processoId))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.PROAP[0].diasCorridos")
                        .value(5))
                .andExpect(jsonPath("$.detalhesPermanenciaPorSetor.PROAP[0].aberta")
                        .value(true));

        movimentar(token, processoId, HOJE.minusDays(1), dipacId, "Retorno posterior");
        assertThat(baixar(token, csvId)).isEqualTo(csvOriginal);
        mockMvc.perform(get("/api/v1/relatorios/{id}/arquivo", csvId))
                .andExpect(status().isUnauthorized());
        assertThat(auditoria.findAll())
                .anySatisfy(registro -> {
                    assertThat(registro.getAcao()).isEqualTo("GERAR_RELATORIO");
                    assertThat(registro.getEntidadeId()).isEqualTo(csvId);
                })
                .anySatisfy(registro -> {
                    assertThat(registro.getAcao()).isEqualTo("DOWNLOAD_RELATORIO");
                    assertThat(registro.getEntidadeId()).isEqualTo(csvId);
                });
    }

    @Test
    void vigenciasSeparamContratoETedEConciliaComAlertasDoDashboardGerencialNosTresFormatos()
            throws Exception {
        String token = tokenAdministradorPermanente();
        formalizar(token, criarProcesso(token, "PROC-VIG-121-019", "DIPAC"),
                "IC-VIG-121/2026", HOJE.plusDays(121), HOJE.plusDays(120));
        formalizar(token, criarProcesso(token, "PROC-VIG-120-019", "DIPAC"),
                "IC-VIG-120/2026", HOJE.plusDays(120), HOJE.plusDays(121));
        formalizar(token, criarProcesso(token, "PROC-VIG-0-019", "DIPAC"),
                "IC-VIG-0/2026", HOJE, null);
        formalizar(token, criarProcesso(token, "PROC-VIG-VENCIDA-019", "DIPAC"),
                "IC-VIG-VENCIDA/2026", HOJE.minusDays(1), HOJE.minusDays(1));

        Map<String, String> filtros = Map.of("origem", "DIPAC");
        long csvId = json(gerar(token, "VIGENCIAS", "CSV", filtros)).get("id").asLong();
        long pdfId = json(gerar(token, "VIGENCIAS", "PDF", filtros)).get("id").asLong();
        long xlsxId = json(gerar(token, "VIGENCIAS", "XLSX", filtros)).get("id").asLong();
        String csv = new String(baixar(token, csvId), StandardCharsets.UTF_8);
        String pdf;
        try (var documento = Loader.loadPDF(baixar(token, pdfId))) {
            pdf = new PDFTextStripper().getText(documento);
        }
        String xlsx = conteudoPlanilha(baixar(token, xlsxId));

        assertThat(csv).contains(
                "numero_processo;tipo_instrumento;vigencia_contratual;situacao_contratual;"
                        + "dias_ate_vencimento_contratual;no_horizonte_120_contratual;vigencia_ted;"
                        + "situacao_ted;dias_ate_vencimento_ted;no_horizonte_120_ted");
        assertThat(csv)
                .contains("PROC-VIG-121-019;CONVENIO;2026-12-07;VALIDA;121;false;2026-12-06;"
                        + "PROXIMA_VENCIMENTO;120;true")
                .contains("PROC-VIG-120-019;CONVENIO;2026-12-06;PROXIMA_VENCIMENTO;120;true;"
                        + "2026-12-07;VALIDA;121;false")
                .contains("PROC-VIG-0-019;CONVENIO;2026-08-08;PROXIMA_VENCIMENTO;0;true;;"
                        + "NAO_INFORMADA;;false")
                .contains("PROC-VIG-VENCIDA-019;CONVENIO;2026-08-07;VENCIDA;-1;false;2026-08-07;"
                        + "VENCIDA;-1;false");

        String somenteContratoValido = new String(baixar(token, json(gerar(
                token, "VIGENCIAS", "CSV", Map.of("vigenciaContratual", "VALIDA")))
                .get("id").asLong()), StandardCharsets.UTF_8);
        assertThat(somenteContratoValido)
                .contains("PROC-VIG-121-019")
                .doesNotContain("PROC-VIG-120-019");
        String somenteTedValido = new String(baixar(token, json(gerar(
                token, "VIGENCIAS", "CSV", Map.of("vigenciaTed", "VALIDA")))
                .get("id").asLong()), StandardCharsets.UTF_8);
        assertThat(somenteTedValido)
                .contains("PROC-VIG-120-019")
                .doesNotContain("PROC-VIG-121-019");

        for (String evidencia : List.of(
                "Relatorio de vigencias", "PROC-VIG-121-019", "PROC-VIG-120-019",
                "PROXIMA_VENCIMENTO", "dias_ate_vencimento_contratual")) {
            assertThat(removerAcentos(csv)).contains(evidencia);
            assertThat(removerAcentos(pdf)).contains(evidencia);
            assertThat(removerAcentos(xlsx)).contains(evidencia);
        }

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertasContratuais").value(2))
                .andExpect(jsonPath("$.alertasTed").value(1));
    }

    private long criarSetor(String token, String sigla, String nome) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/admin/setores")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sigla", sigla, "nome", nome))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private long criarProcesso(String token, String numero, String origem) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/processos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "numero", numero,
                                "origem", origem))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
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

    private void formalizar(
            String token, long processoId, String numeroInstrumento,
            LocalDate vigenciaContratual, LocalDate vigenciaTed) throws Exception {
        long documentoId = criarDocumento(token, processoId);
        var request = objectMapper.createObjectNode();
        request.put("numero", numeroInstrumento);
        request.put("tipo", "CONVENIO");
        request.put("objeto", "Cooperacao institucional");
        request.put("descricao", "Instrumento para relatorio de vigencias");
        request.put("natureza", "Administrativa");
        request.put("coordenador", "Maria Silva");
        request.putArray("participes").add("UFGD").add("Fundacao");
        request.put("valorAtual", 1_000);
        request.put("vigenciaContratualFinal", vigenciaContratual.toString());
        if (vigenciaTed == null) request.putNull("vigenciaTedFinal");
        else request.put("vigenciaTedFinal", vigenciaTed.toString());
        request.put("dataFormalizacao", HOJE.minusMonths(1).toString());
        request.put("documentoAssinadoId", documentoId);
        mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isCreated());
    }

    private long criarDocumento(String token, long processoId) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "instrumento-019.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8));
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

    private MvcResult gerar(
            String token, String tipo, String formato, Map<String, String> filtros) throws Exception {
        return mockMvc.perform(post("/api/v1/relatorios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", tipo,
                                "formato", formato,
                                "filtros", filtros))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private byte[] baixar(String token, long relatorioId) throws Exception {
        return mockMvc.perform(get("/api/v1/relatorios/{id}/arquivo", relatorioId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private String conteudoPlanilha(byte[] arquivo) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(arquivo))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("xl/worksheets/sheet1.xml")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("A planilha nao contem a primeira aba.");
    }

    private String removerAcentos(String texto) {
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
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
