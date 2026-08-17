package com.moments.sicc.api;

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

        byte[] pdf = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        mockMvc.perform(upload(token, processoId, "parece-texto.txt", pdf))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versoes[0].tipoMime").value("application/pdf"))
                .andExpect(jsonPath("$.versoes[0].checksumSha256")
                        .value("4f1949e95440af0ece666ebd5f399c1d77d22de639950784d349fa5feb47dca5"));

        mockMvc.perform(upload(token, processoId, "documento.bin", docxValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versoes[0].tipoMime")
                        .value("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

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
                        "pdf-incompleto.pdf",
                        "%PDF-1.4\nconteúdo truncado".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Formato real não permitido. Use PDF, DOCX, XLSX ou CSV."));
    }

    @Test
    void documentoAdministrativoSomentePodePertencerAProcesso() throws Exception {
        String token = tokenAdministradorPermanente();
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo",
                "administrativo.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));

        mockMvc.perform(multipart("/api/v1/documentos")
                        .file(arquivo)
                        .param("proprietarioTipo", "INSTRUMENTO")
                        .param("proprietarioId", "999")
                        .param("categoria", "ADMINISTRATIVO")
                        .param("titulo", "Documento administrativo")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem")
                        .value("Documento administrativo deve pertencer a um processo."));
    }

    @Test
    void usuariosInternosVersionamBaixamEDesativamDocumentoComAuditoria() throws Exception {
        String tokenAdministrador = tokenAdministradorPermanente();
        UsuarioAutenticado operador = criarOperador(tokenAdministrador);
        long processoId = criarProcesso(tokenAdministrador);
        byte[] versaoInicial = "%PDF-1.4\nversão inicial\n%%EOF"
                .getBytes(StandardCharsets.UTF_8);

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
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", nome, MediaType.APPLICATION_OCTET_STREAM_VALUE, conteudo);
        return multipart("/api/v1/documentos")
                .file(arquivo)
                .param("proprietarioTipo", "PROCESSO")
                .param("proprietarioId", Long.toString(processoId))
                .param("categoria", "ADMINISTRATIVO")
                .param("titulo", nome)
                .header("Authorization", bearer(token));
    }

    private byte[] docxValido() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="%s"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """.formatted(OOXML_CONTENT_TYPES));
        entries.put("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                    Target="word/document.xml"/>
                </Relationships>
                """);
        entries.put("word/document.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body><w:p><w:r><w:t>SICC</w:t></w:r></w:p></w:body>
                </w:document>
                """);
        return zip(entries);
    }

    private byte[] xlsxValido() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="%s"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                </Types>
                """.formatted(OOXML_CONTENT_TYPES));
        entries.put("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                    Target="xl/workbook.xml"/>
                </Relationships>
                """);
        entries.put("xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheets/>
                </workbook>
                """);
        return zip(entries);
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

    private byte[] pdfComTamanho(int tamanho) {
        byte[] content = new byte[tamanho];
        Arrays.fill(content, (byte) ' ');
        byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        byte[] trailer = "\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, content, 0, header.length);
        System.arraycopy(trailer, 0, content, content.length - trailer.length, trailer.length);
        return content;
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
