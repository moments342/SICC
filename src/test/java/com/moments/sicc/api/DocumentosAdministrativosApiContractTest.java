package com.moments.sicc.api;

import static com.moments.sicc.support.ArquivoDocumentoTeste.pdfValido;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moments.sicc.repository.RegistroAuditoriaRepository;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-documentos;MODE=PostgreSQL")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DocumentosAdministrativosApiContractTest extends DocumentoApiContractTestSupport {

    private static final String OOXML_CONTENT_TYPES =
            "application/vnd.openxmlformats-package.relationships+xml";

    @Autowired
    private RegistroAuditoriaRepository auditoria;

    @Test
    void formatosPermitidosSaoIdentificadosPeloConteudoReal() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);

        byte[] pdf = pdfValido();
        mockMvc.perform(upload(token, processoId, "parece-texto.txt", pdf))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versoes[0].tipoMime").value("application/pdf"));

        mockMvc.perform(upload(token, processoId, "documento.bin", docxValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versoes[0].tipoMime")
                        .value("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "documento-malformado.docx",
                        docxComXmlPrincipal("<w:document>")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Formato real não permitido. Use PDF, DOCX, XLSX ou CSV."));

        mockMvc.perform(upload(token, processoId, "planilha.bin", xlsxValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versoes[0].tipoMime")
                        .value("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "dados.bin",
                        "numero,origem\nPA-001,DIPAC\n".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versoes[0].tipoMime").value("text/csv"));

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "zip-disfarcado.docx",
                        zip(Map.of("word/qualquer.txt", "não é um DOCX"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Formato real não permitido. Use PDF, DOCX, XLSX ou CSV."));

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "pdf-falso.pdf",
                        "%PDF-1.4\ntexto arbitrario\n%%EOF".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Formato real não permitido. Use PDF, DOCX, XLSX ou CSV."));

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "pdf-incompleto.pdf",
                        "%PDF-1.4\nconteúdo truncado".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Formato real não permitido. Use PDF, DOCX, XLSX ou CSV."));
    }

    @Test
    void pacoteOoxmlComEntradaDuplicadaEhRejeitado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        byte[] docxComDocumentoDuplicado = substituirAscii(
                zip(
                        "[Content_Types].xml", contentTypesDocx(),
                        "_rels/.rels", relacionamentosDocx(),
                        "word/document.dup", "<conteudo-invalido/>",
                        "word/document.xml", xmlDocxValido()),
                "word/document.dup",
                "word/document.xml");

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "documento-duplicado.docx",
                        docxComDocumentoDuplicado))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Arquivo compactado inválido."));
    }

    @Test
    void pacoteOoxmlSemDiretorioCentralEhRejeitado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        byte[] docxSemDiretorioCentral = truncarAntesDoDiretorioCentral(docxValido());

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "documento-sem-diretorio-central.docx",
                        docxSemDiretorioCentral))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Arquivo compactado inválido."));
    }

    @Test
    void pacoteOoxmlComConteudoDivergenteNoDiretorioCentralEhRejeitado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        Map<String, String> entries = entradasDocx(xmlDocxValido());
        entries.put("custom/a.bin", "AAAAAAAAAAAAAAAA");
        entries.put("custom/b.bin", "BBBBBBBBBBBBBBBB");
        byte[] docxDivergente = trocarOffsetsNoDiretorioCentral(
                zip(entries), "custom/a.bin", "custom/b.bin");

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "documento-divergente.docx",
                        docxDivergente))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Arquivo compactado inválido."));
    }

    @Test
    void pacoteOoxmlComSeparadorAmbiguoEhRejeitado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        byte[] docxComSeparadorInvertido = zip(
                "[Content_Types].xml", contentTypesDocx(),
                "_rels\\.rels", relacionamentosDocx(),
                "word/document.xml", xmlDocxValido());

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "documento-com-separador-ambiguo.docx",
                        docxComSeparadorInvertido))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Arquivo compactado inválido."));
    }

    @Test
    void pacoteOoxmlComTraversalEmSegmentoTerminalEhRejeitado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        byte[] docxComTraversal = zip(
                "[Content_Types].xml", contentTypesDocx(),
                "_rels/.rels", relacionamentosDocx(),
                "word/document.xml", xmlDocxValido(),
                "word/..", "");

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "documento-com-traversal.docx",
                        docxComTraversal))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Arquivo compactado inválido."));
    }

    @Test
    void pacoteOoxmlComMaisDeMilEVinteEQuatroEntradasEhRejeitado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "documento-com-muitas-entradas.docx",
                        docxComQuantidadeEntradas(1_025)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Arquivo compactado inválido."));
    }

    @Test
    void xmlPrincipalOoxmlMaiorQueOitoMebibytesEhRejeitado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        String xmlGrande = """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body><!--%s--></w:body>
                </w:document>
                """.formatted("x".repeat(8 * 1024 * 1024));

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "documento-com-xml-grande.docx",
                        docxComXmlPrincipal(xmlGrande)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Arquivo compactado inválido."));
    }

    @Test
    void manifestosOoxmlExigemRaizesExatas() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        String contentTypesComRaizIncorreta = contentTypesDocx()
                .replace("<Types ", "<Envelope ")
                .replace("</Types>", "</Envelope>");
        String relacionamentosComRaizIncorreta = relacionamentosDocx()
                .replace("<Relationships ", "<Envelope ")
                .replace("</Relationships>", "</Envelope>");

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "content-types-com-raiz-incorreta.docx",
                        docxComPartes(
                                contentTypesComRaizIncorreta,
                                relacionamentosDocx(),
                                xmlDocxValido())))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(upload(
                        token,
                        processoId,
                        "relacionamentos-com-raiz-incorreta.docx",
                        docxComPartes(
                                contentTypesDocx(),
                                relacionamentosComRaizIncorreta,
                                xmlDocxValido())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void documentoWordSemBodyEhRejeitado() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        String documentoSemBody = """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:background/>
                </w:document>
                """;

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "documento-sem-body.docx",
                        docxComXmlPrincipal(documentoSemBody)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void pastaExcelSemSheetsEhRejeitada() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        String workbookSemSheets = """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <definedNames/>
                </workbook>
                """;

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "planilha-sem-sheets.xlsx",
                        xlsxComXmlPrincipal(workbookSemSheets)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void pastaExcelSemNenhumaPlanilhaEhRejeitada() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        String workbookSemPlanilha = """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheets/>
                </workbook>
                """;

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "planilha-vazia.xlsx",
                        xlsxComXmlPrincipal(workbookSemPlanilha)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void xmlComDoctypeEXxeEhRejeitadoSemEscreverNoStderr() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        String contentTypesComXxe = contentTypesDocx().replace(
                "<Types ",
                "<!DOCTYPE Types [<!ENTITY xxe SYSTEM \"file:///arquivo-que-nao-deve-ser-lido\">]>\n<Types ")
                .replace("</Types>", "&xxe;</Types>");
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalStderr = System.err;

        try (PrintStream capturedStderr =
                new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setErr(capturedStderr);
            mockMvc.perform(upload(
                            token,
                            processoId,
                            "documento-com-xxe.docx",
                            docxComPartes(
                                    contentTypesComXxe,
                                    relacionamentosDocx(),
                                    xmlDocxValido())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.mensagem")
                            .value("Formato real não permitido. Use PDF, DOCX, XLSX ou CSV."));
        } finally {
            System.setErr(originalStderr);
        }
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void documentoAdministrativoPodePertencerAQualquerProprietarioExistente() throws Exception {
        String token = tokenAdministradorPermanente();
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo",
                "administrativo.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfValido());

        mockMvc.perform(multipart("/api/v1/documentos")
                        .file(arquivo)
                        .param("proprietarioTipo", "INSTRUMENTO")
                        .param("proprietarioId", "999")
                        .param("categoria", "ADMINISTRATIVO")
                        .param("titulo", "Documento administrativo")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem")
                        .value("Proprietário do documento não encontrado."));
    }

    @Test
    void processoInativoBloqueiaMutacoesDeDocumentosSemBloquearLeituraHistorica()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        byte[] versaoInicial = pdfValido("versão anterior à inativação");
        MvcResult criado = mockMvc.perform(upload(
                        token,
                        processoId,
                        "administrativo-inativo.pdf",
                        versaoInicial))
                .andExpect(status().isCreated())
                .andReturn();
        long documentoId = json(criado).get("id").asLong();

        desativarProcesso(token, processoId);

        mockMvc.perform(get("/api/v1/documentos")
                        .queryParam("proprietarioTipo", "PROCESSO")
                        .queryParam("proprietarioId", Long.toString(processoId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(documentoId));
        mockMvc.perform(get(
                                "/api/v1/documentos/{id}/versoes/{versao}/arquivo",
                                documentoId,
                                1)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(versaoInicial));

        MockMultipartFile novaVersao = new MockMultipartFile(
                "arquivo",
                "administrativo-inativo-v2.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfValido("versão posterior à inativação"));
        esperarBloqueioPorProcessoInativo(
                multipart("/api/v1/documentos/{id}/versoes", documentoId)
                        .file(novaVersao)
                        .header("Authorization", bearer(token)));
        esperarBloqueioPorProcessoInativo(
                delete("/api/v1/documentos/{id}", documentoId)
                        .header("Authorization", bearer(token)));
        esperarBloqueioPorProcessoInativo(upload(
                token,
                processoId,
                "administrativo-posterior.pdf",
                pdfValido("documento posterior à inativação")));
    }

    @Test
    void processoInativoBloqueiaNovoDocumentoAssociadoIndiretamenteAoInstrumento()
            throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        long documentoAssinadoId = criarDocumento(
                token,
                "PROCESSO",
                processoId,
                "ASSINADO",
                "instrumento-assinado.pdf",
                pdfValido("instrumento assinado"));
        long instrumentoId = formalizarInstrumento(token, processoId, documentoAssinadoId);
        desativarProcesso(token, processoId);

        esperarBloqueioPorProcessoInativo(upload(
                token,
                "INSTRUMENTO",
                instrumentoId,
                "ADMINISTRATIVO",
                "administrativo-do-instrumento.pdf",
                pdfValido("administrativo posterior à inativação")));
    }

    @Test
    void usuariosInternosVersionamBaixamEDesativamDocumentoComAuditoria() throws Exception {
        String tokenAdministrador = tokenAdministradorPermanente();
        UsuarioAutenticado operador = criarOperador(tokenAdministrador);
        long processoId = criarProcesso(tokenAdministrador);
        byte[] versaoInicial = pdfValido("versão inicial");

        MvcResult criado = mockMvc.perform(upload(
                        tokenAdministrador,
                        processoId,
                        "administrativo.pdf",
                        versaoInicial))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criadoPor.id").value(1))
                .andExpect(jsonPath("$.criadoPor.nome").value("Administrador de Teste"))
                .andExpect(jsonPath("$.versoes[0].versao").value(1))
                .andExpect(jsonPath("$.versoes[0].criadoPor.id").value(1))
                .andReturn();
        long documentoId = json(criado).get("id").asLong();
        String checksumInicial = json(criado).get("versoes").get(0).get("checksumSha256").asText();

        byte[] versaoAtual = "numero,origem\nPA-DOC-009/2026,DIPAC\n"
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile csv = new MockMultipartFile(
                "arquivo", "administrativo.csv", MediaType.TEXT_PLAIN_VALUE, versaoAtual);
        mockMvc.perform(multipart("/api/v1/documentos/{id}/versoes", documentoId)
                        .file(csv)
                        .header("Authorization", bearer(operador.token())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versoes[0].versao").value(2))
                .andExpect(jsonPath("$.versoes[0].criadoPor.id").value(operador.id()))
                .andExpect(jsonPath("$.versoes[1].versao").value(1))
                .andExpect(jsonPath("$.versoes[1].checksumSha256").value(checksumInicial))
                .andExpect(jsonPath("$.versoes[1].criadoPor.id").value(1));

        mockMvc.perform(get(
                                "/api/v1/documentos/{id}/versoes/{versao}/arquivo",
                                documentoId,
                                1)
                        .header("Authorization", bearer(operador.token())))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("administrativo.pdf")))
                .andExpect(content().bytes(versaoInicial));
        mockMvc.perform(get(
                                "/api/v1/documentos/{id}/versoes/{versao}/arquivo",
                                documentoId,
                                2)
                        .header("Authorization", bearer(operador.token())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().bytes(versaoAtual));

        mockMvc.perform(delete("/api/v1/documentos/{id}", documentoId)
                        .header("Authorization", bearer(operador.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.versoes.length()").value(2));
        mockMvc.perform(get("/api/v1/documentos")
                        .queryParam("proprietarioTipo", "PROCESSO")
                        .queryParam("proprietarioId", Long.toString(processoId))
                        .header("Authorization", bearer(operador.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/documentos")
                        .queryParam("proprietarioTipo", "PROCESSO")
                        .queryParam("proprietarioId", Long.toString(processoId))
                        .queryParam("incluirInativos", "true")
                        .header("Authorization", bearer(operador.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(documentoId))
                .andExpect(jsonPath("$[0].ativo").value(false))
                .andExpect(jsonPath("$[0].versoes.length()").value(2));
        mockMvc.perform(get(
                                "/api/v1/documentos/{id}/versoes/{versao}/arquivo",
                                documentoId,
                                1)
                        .header("Authorization", bearer(operador.token())))
                .andExpect(status().isOk())
                .andExpect(content().bytes(versaoInicial));

        mockMvc.perform(get("/api/v1/public/processos")
                        .queryParam("numero", "PA-DOC-009/2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentos").doesNotExist());
        mockMvc.perform(get("/api/v1/documentos")
                        .queryParam("proprietarioTipo", "PROCESSO")
                        .queryParam("proprietarioId", Long.toString(processoId)))
                .andExpect(status().isUnauthorized());

        assertThat(auditoria.findByEntidadeAndEntidadeIdOrderByCriadoEmDesc(
                "DOCUMENTO", documentoId))
                .extracting(registro -> registro.getAcao())
                .contains(
                        "CRIAR_DOCUMENTO",
                        "CRIAR_VERSAO_DOCUMENTO",
                        "DOWNLOAD_DOCUMENTO",
                        "DESATIVAR_DOCUMENTO");
    }

    @Test
    void cadaVersaoAceitaAteVinteMebibytesERejeitaUmByteAcima() throws Exception {
        String token = tokenAdministradorPermanente();
        long processoId = criarProcesso(token);
        int limite = 20 * 1024 * 1024;
        byte[] tamanhoMaximo = pdfComTamanho(limite);

        mockMvc.perform(upload(token, processoId, "limite.pdf", tamanhoMaximo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versoes[0].tamanho").value(limite));

        mockMvc.perform(upload(
                        token,
                        processoId,
                        "acima-do-limite.pdf",
                        pdfComTamanho(limite + 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Cada versão deve ter no máximo 20 MB."));

        mockMvc.perform(get("/api/v1/documentos")
                        .queryParam("proprietarioTipo", "PROCESSO")
                        .queryParam("proprietarioId", Long.toString(processoId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private org.springframework.test.web.servlet.RequestBuilder upload(
            String token, long processoId, String nome, byte[] conteudo) {
        return upload(
                token,
                "PROCESSO",
                processoId,
                "ADMINISTRATIVO",
                nome,
                conteudo);
    }

    private org.springframework.test.web.servlet.RequestBuilder upload(
            String token,
            String proprietarioTipo,
            long proprietarioId,
            String categoria,
            String nome,
            byte[] conteudo) {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", nome, MediaType.APPLICATION_OCTET_STREAM_VALUE, conteudo);
        return multipart("/api/v1/documentos")
                .file(arquivo)
                .param("proprietarioTipo", proprietarioTipo)
                .param("proprietarioId", Long.toString(proprietarioId))
                .param("categoria", categoria)
                .param("titulo", nome)
                .header("Authorization", bearer(token));
    }

    private long criarDocumento(
            String token,
            String proprietarioTipo,
            long proprietarioId,
            String categoria,
            String nome,
            byte[] conteudo) throws Exception {
        MvcResult resultado = mockMvc.perform(upload(
                        token,
                        proprietarioTipo,
                        proprietarioId,
                        categoria,
                        nome,
                        conteudo))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private long formalizarInstrumento(
            String token, long processoId, long documentoAssinadoId) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/processos/{id}/instrumento", processoId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "numero":"CV-DOC-INATIVO-001/2026",
                                  "tipo":"CONVENIO",
                                  "objeto":"Cooperação institucional",
                                  "descricao":"Instrumento para contrato de processo inativo",
                                  "natureza":"Administrativa",
                                  "coordenador":"Maria Silva",
                                  "participes":["UFGD","Fundação"],
                                  "valorAtual":150000,
                                  "vigenciaContratualFinal":"2030-08-01",
                                  "vigenciaTedFinal":"2030-04-30",
                                  "dataFormalizacao":"2026-08-01",
                                  "documentoAssinadoId":%d
                                }
                                """.formatted(documentoAssinadoId)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(resultado).get("id").asLong();
    }

    private void desativarProcesso(String token, long processoId) throws Exception {
        mockMvc.perform(delete("/api/v1/processos/{id}", processoId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    private void esperarBloqueioPorProcessoInativo(
            org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("O Processo Administrativo está inativo."));
    }

    private byte[] docxValido() throws Exception {
        return docxComXmlPrincipal(xmlDocxValido());
    }

    private String xmlDocxValido() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body><w:p><w:r><w:t>SICC</w:t></w:r></w:p></w:body>
                </w:document>
                """;
    }

    private byte[] docxComXmlPrincipal(String xmlPrincipal) throws Exception {
        return zip(entradasDocx(xmlPrincipal));
    }

    private byte[] docxComPartes(
            String contentTypes, String relacionamentos, String xmlPrincipal) throws Exception {
        return zip(
                "[Content_Types].xml", contentTypes,
                "_rels/.rels", relacionamentos,
                "word/document.xml", xmlPrincipal);
    }

    private byte[] docxComQuantidadeEntradas(int quantidade) throws Exception {
        Map<String, String> entries = entradasDocx(xmlDocxValido());
        for (int i = entries.size(); i < quantidade; i++) {
            entries.put("customXml/item" + i + ".xml", "<item/>");
        }
        return zip(entries);
    }

    private Map<String, String> entradasDocx(String xmlPrincipal) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", contentTypesDocx());
        entries.put("_rels/.rels", relacionamentosDocx());
        entries.put("word/document.xml", xmlPrincipal);
        return entries;
    }

    private String contentTypesDocx() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="%s"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """.formatted(OOXML_CONTENT_TYPES);
    }

    private String relacionamentosDocx() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                    Target="word/document.xml"/>
                </Relationships>
                """;
    }

    private byte[] xlsxValido() throws Exception {
        return xlsxComXmlPrincipal(xmlXlsxValido());
    }

    private byte[] xlsxComXmlPrincipal(String xmlPrincipal) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", contentTypesXlsx());
        entries.put("_rels/.rels", relacionamentosXlsx());
        entries.put("xl/workbook.xml", xmlPrincipal);
        entries.put("xl/_rels/workbook.xml.rels", relacionamentosWorkbookXlsx());
        entries.put("xl/worksheets/sheet1.xml", xmlWorksheetValido());
        return zip(entries);
    }

    private String contentTypesXlsx() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="%s"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """.formatted(OOXML_CONTENT_TYPES);
    }

    private String relacionamentosXlsx() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                    Target="xl/workbook.xml"/>
                </Relationships>
                """;
    }

    private String relacionamentosWorkbookXlsx() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
                    Target="worksheets/sheet1.xml"/>
                </Relationships>
                """;
    }

    private String xmlXlsxValido() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Planilha1" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
                """;
    }

    private String xmlWorksheetValido() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData/>
                </worksheet>
                """;
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] zip(String... nomesEConteudos) throws Exception {
        if (nomesEConteudos.length % 2 != 0) {
            throw new IllegalArgumentException("Cada entrada ZIP exige nome e conteúdo.");
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < nomesEConteudos.length; i += 2) {
            entries.put(nomesEConteudos[i], nomesEConteudos[i + 1]);
        }
        return zip(entries);
    }

    private byte[] substituirAscii(byte[] content, String origem, String destino) {
        byte[] antigo = origem.getBytes(StandardCharsets.US_ASCII);
        byte[] novo = destino.getBytes(StandardCharsets.US_ASCII);
        if (antigo.length != novo.length) {
            throw new IllegalArgumentException("Os nomes ZIP devem ter o mesmo tamanho.");
        }
        for (int i = 0; i <= content.length - antigo.length; i++) {
            boolean encontrou = true;
            for (int j = 0; j < antigo.length; j++) {
                if (content[i + j] != antigo[j]) {
                    encontrou = false;
                    break;
                }
            }
            if (encontrou) {
                System.arraycopy(novo, 0, content, i, novo.length);
                i += antigo.length - 1;
            }
        }
        return content;
    }

    private byte[] truncarAntesDoDiretorioCentral(byte[] content) {
        byte[] assinaturaDiretorioCentral = {'P', 'K', 1, 2};
        int inicioDiretorioCentral = primeiraOcorrencia(content, assinaturaDiretorioCentral);
        return Arrays.copyOf(content, inicioDiretorioCentral);
    }

    private byte[] trocarOffsetsNoDiretorioCentral(byte[] content, String nomeA, String nomeB) {
        int entradaA = entradaDiretorioCentral(content, nomeA);
        int entradaB = entradaDiretorioCentral(content, nomeB);
        int offsetA = inteiroLittleEndian(content, entradaA + 42);
        int offsetB = inteiroLittleEndian(content, entradaB + 42);
        escreverInteiroLittleEndian(content, entradaA + 42, offsetB);
        escreverInteiroLittleEndian(content, entradaB + 42, offsetA);
        return content;
    }

    private int entradaDiretorioCentral(byte[] content, String nomeEsperado) {
        byte[] assinatura = {'P', 'K', 1, 2};
        for (int i = 0; i <= content.length - 46; i++) {
            if (!Arrays.equals(content, i, i + assinatura.length, assinatura, 0, assinatura.length)) {
                continue;
            }
            int tamanhoNome = inteiroCurtoLittleEndian(content, i + 28);
            String nome = new String(content, i + 46, tamanhoNome, StandardCharsets.UTF_8);
            if (nomeEsperado.equals(nome)) return i;
        }
        throw new IllegalArgumentException("Entrada esperada não encontrada no diretório central.");
    }

    private int inteiroCurtoLittleEndian(byte[] content, int inicio) {
        return Byte.toUnsignedInt(content[inicio])
                | Byte.toUnsignedInt(content[inicio + 1]) << 8;
    }

    private int inteiroLittleEndian(byte[] content, int inicio) {
        return inteiroCurtoLittleEndian(content, inicio)
                | inteiroCurtoLittleEndian(content, inicio + 2) << 16;
    }

    private void escreverInteiroLittleEndian(byte[] content, int inicio, int valor) {
        content[inicio] = (byte) valor;
        content[inicio + 1] = (byte) (valor >>> 8);
        content[inicio + 2] = (byte) (valor >>> 16);
        content[inicio + 3] = (byte) (valor >>> 24);
    }

    private int primeiraOcorrencia(byte[] content, byte[] value) {
        for (int i = 0; i <= content.length - value.length; i++) {
            boolean match = true;
            for (int j = 0; j < value.length; j++) {
                if (content[i + j] != value[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        throw new IllegalArgumentException("Marcador esperado não encontrado.");
    }

    private byte[] pdfComTamanho(int tamanho) {
        byte[] base = pdfValido("limite de tamanho");
        if (tamanho < base.length) {
            throw new IllegalArgumentException("O tamanho deve comportar o PDF base.");
        }
        byte[] eof = "%%EOF".getBytes(StandardCharsets.US_ASCII);
        int eofPosition = ultimaOcorrencia(base, eof);
        int padding = tamanho - base.length;
        byte[] content = new byte[tamanho];
        System.arraycopy(base, 0, content, 0, eofPosition);
        Arrays.fill(content, eofPosition, eofPosition + padding, (byte) ' ');
        System.arraycopy(
                base,
                eofPosition,
                content,
                eofPosition + padding,
                base.length - eofPosition);
        return content;
    }

    private int ultimaOcorrencia(byte[] content, byte[] value) {
        for (int i = content.length - value.length; i >= 0; i--) {
            boolean match = true;
            for (int j = 0; j < value.length; j++) {
                if (content[i + j] != value[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        throw new IllegalArgumentException("Marcador esperado não encontrado.");
    }

    private UsuarioAutenticado criarOperador(String tokenAdministrador) throws Exception {
        MvcResult criado = mockMvc.perform(post("/api/v1/admin/usuarios")
                        .header("Authorization", bearer(tokenAdministrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Operador de Documentos",
                                  "email":"documentos@ufgd.edu.br",
                                  "login":"operador.documentos",
                                  "senhaTemporaria":"Operador123!",
                                  "perfil":"OPERADOR_DIPAC"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long id = json(criado).get("id").asLong();
        String tokenTemporario = tokenDoLogin(
                "operador.documentos", "Operador123!", true);
        mockMvc.perform(post("/api/v1/auth/senha")
                        .header("Authorization", bearer(tokenTemporario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual":"Operador123!","novaSenha":"Operador456!"}
                                """))
                .andExpect(status().isNoContent());
        return new UsuarioAutenticado(
                id, tokenDoLogin("operador.documentos", "Operador456!", false));
    }

    private record UsuarioAutenticado(long id, String token) {}
}
