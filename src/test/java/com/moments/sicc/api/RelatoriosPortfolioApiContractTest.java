package com.moments.sicc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moments.sicc.repository.RegistroAuditoriaRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
        "spring.datasource.url=jdbc:h2:mem:sicc-relatorios-portfolio;MODE=PostgreSQL")
@AutoConfigureMockMvc
@Import(RelatoriosPortfolioApiContractTest.RelogioFixoConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RelatoriosPortfolioApiContractTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 8, 8);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RegistroAuditoriaRepository auditoria;

    @Test
    void geraRelatoriosDoPortfolioComFiltrosEstadoReconstruidoHistoricoEAutorizacao()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoIncluido = criarProcesso(token, "PROC-REL-018", "DIPAC");
        long instrumentoIncluido = formalizar(
                token, processoIncluido, "CONVENIO", 1_000, HOJE.plusYears(1));
        efetivarTermo(token, instrumentoIncluido, 1_000, 1_500);

        long processoExcluido = criarProcesso(token, "PROC-FORA-REL-018", "PROAP");
        formalizar(token, processoExcluido, "CONTRATO_GESTAO", 9_000, HOJE.plusYears(1));

        MvcResult consolidado = gerar(token, "CONSOLIDADO", "CSV", Map.of(
                "origem", "DIPAC",
                "tipo", "CONVENIO"));
        long consolidadoId = json(consolidado).get("id").asLong();
        String checksum = json(consolidado).get("checksumSha256").asText();

        mockMvc.perform(post("/api/v1/relatorios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "ANUAL_PROCESSOS",
                                "formato", "CSV",
                                "filtros", Map.of("ano", "2026", "origem", "DIPAC")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ANUAL_PROCESSOS"));
        mockMvc.perform(post("/api/v1/relatorios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "INSTRUMENTOS_POR_TIPO",
                                "formato", "CSV",
                                "filtros", Map.of("tipo", "CONVENIO")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("INSTRUMENTOS_POR_TIPO"));

        MvcResult arquivo = mockMvc.perform(get("/api/v1/relatorios/{id}/arquivo", consolidadoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        byte[] conteudo = arquivo.getResponse().getContentAsByteArray();
        String csv = new String(conteudo, StandardCharsets.UTF_8);
        assertThat(csv)
                .contains("PROC-REL-018")
                .contains("1500.00")
                .doesNotContain("PROC-FORA-REL-018")
                .doesNotContain("1000.00");
        assertThat(checksum).isEqualTo(sha256(conteudo));

        efetivarTermo(token, instrumentoIncluido, 1_500, 2_100);
        gerar(token, "CONSOLIDADO", "CSV", Map.of("origem", "DIPAC", "tipo", "CONVENIO"));
        byte[] conteudoHistorico = baixar(token, consolidadoId);
        assertThat(conteudoHistorico).isEqualTo(conteudo);
        assertThat(new String(conteudoHistorico, StandardCharsets.UTF_8))
                .contains("1500.00")
                .doesNotContain("2100.00");

        mockMvc.perform(get("/api/v1/relatorios")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.id == " + consolidadoId + ")].filtros.origem")
                        .value("DIPAC"))
                .andExpect(jsonPath("$[?(@.id == " + consolidadoId + ")].criadoPor.login")
                        .value("admin"))
                .andExpect(jsonPath("$[?(@.id == " + consolidadoId + ")].checksumSha256")
                        .value(checksum))
                .andExpect(jsonPath("$[?(@.id == " + consolidadoId + ")].chaveArmazenamento")
                        .isNotEmpty());

        assertThat(auditoria.findAll())
                .anySatisfy(registro -> {
                    assertThat(registro.getAcao()).isEqualTo("GERAR_RELATORIO");
                    assertThat(registro.getEntidadeId()).isEqualTo(consolidadoId);
                    assertThat(registro.isSucesso()).isTrue();
                })
                .anySatisfy(registro -> {
                    assertThat(registro.getAcao()).isEqualTo("DOWNLOAD_RELATORIO");
                    assertThat(registro.getEntidadeId()).isEqualTo(consolidadoId);
                    assertThat(registro.isSucesso()).isTrue();
                });

        mockMvc.perform(get("/api/v1/relatorios"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/relatorios/{id}/arquivo", consolidadoId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mesmaSelecaoTemConteudoEMetadadosLegiveisEmCsvPdfEXlsx() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token, "PROC-FORMATOS-018-Ж", "DIPAC – Relatórios");
        formalizar(token, processoId, "CONVENIO", 2_750, HOJE.plusYears(1));
        Map<String, String> filtros = Map.of("origem", "DIPAC – Relatórios", "tipo", "CONVENIO");

        long csvId = json(gerar(token, "CONSOLIDADO", "CSV", filtros)).get("id").asLong();
        long pdfId = json(gerar(token, "CONSOLIDADO", "PDF", filtros)).get("id").asLong();
        long xlsxId = json(gerar(token, "CONSOLIDADO", "XLSX", filtros)).get("id").asLong();

        String csv = new String(baixar(token, csvId), StandardCharsets.UTF_8);
        String pdf;
        try (var documento = Loader.loadPDF(baixar(token, pdfId))) {
            pdf = new PDFTextStripper().getText(documento);
        }
        String xlsx = conteudoPlanilha(baixar(token, xlsxId));

        List<String> evidenciasEquivalentes = List.of(
                "Relatório consolidado",
                "origem=DIPAC – Relatórios | tipo=CONVENIO",
                "Administrador de Teste (admin)",
                "2026-08-08T12:00:00",
                "numero_processo",
                "PROC-FORMATOS-018-Ж",
                "2750.00");
        for (String evidencia : evidenciasEquivalentes) {
            assertThat(csv).contains(evidencia);
            assertThat(pdf).contains(evidencia);
            assertThat(xlsx).contains(evidencia);
        }
    }

    @Test
    void rejeitaFiltrosQueNaoSaoAplicaveisAoTipoDoRelatorio() throws Exception {
        String token = tokenAdministradorPermanente();

        mockMvc.perform(post("/api/v1/relatorios")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tipo", "CONSOLIDADO",
                                "formato", "CSV",
                                "filtros", Map.of("ano", "2026", "desconhecido", "valor")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(
                        "Filtros não aplicáveis a CONSOLIDADO: ano, desconhecido."));
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
                .andExpect(jsonPath("$.tipo").value(tipo))
                .andExpect(jsonPath("$.formato").value(formato))
                .andExpect(jsonPath("$.filtros.origem").value(filtros.get("origem")))
                .andExpect(jsonPath("$.criadoPor.login").value("admin"))
                .andExpect(jsonPath("$.criadoEm").value("2026-08-08T12:00:00"))
                .andExpect(jsonPath("$.checksumSha256").isString())
                .andExpect(jsonPath("$.chaveArmazenamento").isString())
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
        throw new AssertionError("A planilha não contém a primeira aba.");
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

    private long formalizar(
            String token, long processoId, String tipo, int valor, LocalDate vigencia) throws Exception {
        long documentoId = criarDocumento(token, "PROCESSO", processoId,
                "instrumento-" + processoId + ".pdf", "Instrumento assinado");
        ObjectNode request = objectMapper.createObjectNode();
        request.put("numero", "IC-" + processoId + "/2026");
        request.put("tipo", tipo);
        request.put("objeto", "Cooperacao institucional");
        request.put("descricao", "Instrumento do portfolio contratual");
        request.put("natureza", "Administrativa");
        request.put("coordenador", "Maria Silva");
        request.putArray("participes").add("UFGD").add("Fundacao");
        request.put("valorAtual", valor);
        request.put("vigenciaContratualFinal", vigencia.toString());
        request.put("dataFormalizacao", HOJE.minusMonths(1).toString());
        request.put("documentoAssinadoId", documentoId);

        MvcResult resultado = mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private void efetivarTermo(
            String token, long instrumentoId, int valorAnterior, int valorNovo) throws Exception {
        MvcResult criado = mockMvc.perform(post("/api/v1/alteracoes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoId", instrumentoId,
                                "tipo", "TERMO_ADITIVO",
                                "numeroOficial", "TA-REL-018-" + valorNovo + "/2026",
                                "operacao", "ORIGINAL",
                                "mudancas", List.of(Map.of(
                                        "campo", "VALOR_ATUAL",
                                        "valorAnterior", valorAnterior + ".00",
                                        "valorNovo", valorNovo + ".00"))))))
                .andExpect(status().isCreated())
                .andReturn();
        long termoId = json(criado).get("id").asLong();
        long documentoId = criarDocumento(token, "TERMO_ADITIVO", termoId,
                "termo-018.pdf", "Termo aditivo assinado");

        mockMvc.perform(post("/api/v1/alteracoes/{id}/efetivacao", termoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataEfetivacao", HOJE.minusDays(1).toString(),
                                "ordemOficial", valorNovo == 1_500 ? 1 : 2,
                                "documentoAssinadoId", documentoId))))
                .andExpect(status().isOk());
    }

    private long criarDocumento(
            String token, String proprietarioTipo, long proprietarioId,
            String nomeArquivo, String titulo) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", nomeArquivo, MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8));
        MvcResult resultado = mockMvc.perform(multipart("/api/v1/documentos")
                        .file(arquivo)
                        .param("proprietarioTipo", proprietarioTipo)
                        .param("proprietarioId", Long.toString(proprietarioId))
                        .param("categoria", "ASSINADO")
                        .param("titulo", titulo)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
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

    private String sha256(byte[] conteudo) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
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
