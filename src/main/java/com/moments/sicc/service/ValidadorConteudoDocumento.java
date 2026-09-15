package com.moments.sicc.service;

import com.moments.sicc.shared.exception.DomainException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

@Component
public class ValidadorConteudoDocumento {
    private static final int MAX_OOXML_ENTRIES = 1_024;
    private static final int MAX_CONTENT_TYPES_BYTES = 64 * 1024;
    private static final int MAX_RELATIONSHIPS_BYTES = 256 * 1024;
    private static final int MAX_MAIN_XML_BYTES = 8 * 1024 * 1024;
    private static final int MAX_OOXML_UNCOMPRESSED_BYTES = 32 * 1024 * 1024;
    private static final String CONTENT_TYPES_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String OFFICE_DOCUMENT_RELATIONSHIP =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    private static final String OFFICE_DOCUMENT_RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String WORKSHEET_RELATIONSHIP =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet";
    private static final String WORD_NAMESPACE =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String SPREADSHEET_NAMESPACE =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String DOCX_MAIN_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String XLSX_MAIN_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";
    private static final String WORKSHEET_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml";
    private static final PerfilOoxml PERFIL_DOCX = new PerfilOoxml(
            "/word/document.xml",
            "word/document.xml",
            DOCX_MAIN_CONTENT_TYPE,
            WORD_NAMESPACE,
            "document",
            "body");
    private static final PerfilOoxml PERFIL_XLSX = new PerfilOoxml(
            "/xl/workbook.xml",
            "xl/workbook.xml",
            XLSX_MAIN_CONTENT_TYPE,
            SPREADSHEET_NAMESPACE,
            "workbook",
            "sheets");
    private static final ErrorHandler REJECTING_XML_ERROR_HANDLER = new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    };

    public FormatoDocumento detectar(byte[] content) {
        if (pdfValido(content)) return FormatoDocumento.PDF;
        FormatoDocumento ooxml = detectarOoxml(content);
        if (ooxml != null) return ooxml;
        if (csvValido(content)) return FormatoDocumento.CSV;
        throw new DomainException("Formato real não permitido. Use PDF, DOCX, XLSX ou CSV.");
    }

    private boolean pdfValido(byte[] content) {
        if (!startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            return document.getNumberOfPages() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private FormatoDocumento detectarOoxml(byte[] content) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') return null;
        Map<String, String> checksumsDiretorioCentral = validarDiretorioCentral(content);
        byte[] relationships = null;
        byte[] wordMain = null;
        byte[] excelMain = null;
        byte[] excelRelationships = null;
        byte[] contentTypes = null;
        Map<String, byte[]> worksheets = new HashMap<>();
        int entries = 0;
        long[] uncompressedBytes = {0};
        Set<String> entryNames = new HashSet<>();
        Map<String, String> checksumsEntradasLocais = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_OOXML_ENTRIES) {
                    throw new DomainException("Arquivo compactado inválido.");
                }
                String name = nomeEntradaValido(entry, entryNames);
                EntradaLida entradaLida;
                if ("[Content_Types].xml".equals(name)) {
                    entradaLida = lerEntradaLimitada(
                            zip, MAX_CONTENT_TYPES_BYTES, uncompressedBytes, true);
                    contentTypes = entradaLida.conteudo();
                } else if ("_rels/.rels".equals(name)) {
                    entradaLida = lerEntradaLimitada(
                            zip, MAX_RELATIONSHIPS_BYTES, uncompressedBytes, true);
                    relationships = entradaLida.conteudo();
                } else if ("word/document.xml".equals(name)) {
                    entradaLida = lerEntradaLimitada(
                            zip, MAX_MAIN_XML_BYTES, uncompressedBytes, true);
                    wordMain = entradaLida.conteudo();
                } else if ("xl/workbook.xml".equals(name)) {
                    entradaLida = lerEntradaLimitada(
                            zip, MAX_MAIN_XML_BYTES, uncompressedBytes, true);
                    excelMain = entradaLida.conteudo();
                } else if ("xl/_rels/workbook.xml.rels".equals(name)) {
                    entradaLida = lerEntradaLimitada(
                            zip, MAX_RELATIONSHIPS_BYTES, uncompressedBytes, true);
                    excelRelationships = entradaLida.conteudo();
                } else if (name.startsWith("xl/worksheets/") && name.endsWith(".xml")) {
                    entradaLida = lerEntradaLimitada(
                            zip, MAX_MAIN_XML_BYTES, uncompressedBytes, true);
                    worksheets.put(name, entradaLida.conteudo());
                } else {
                    entradaLida = lerEntradaLimitada(
                            zip, MAX_OOXML_UNCOMPRESSED_BYTES, uncompressedBytes, false);
                }
                checksumsEntradasLocais.put(name, entradaLida.checksumSha256());
            }
            if (!checksumsEntradasLocais.equals(checksumsDiretorioCentral)) {
                throw new DomainException("Arquivo compactado inválido.");
            }
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("Arquivo compactado inválido.");
        }
        if (relationships == null || contentTypes == null || (wordMain == null) == (excelMain == null)) {
            return null;
        }
        PacoteOoxml pacoteWord = wordMain == null
                ? null
                : analisarPacoteOoxml(contentTypes, relationships, wordMain, PERFIL_DOCX);
        if (pacoteWord != null) {
            return FormatoDocumento.DOCX;
        }
        PacoteOoxml pacoteExcel = excelMain == null
                ? null
                : analisarPacoteOoxml(contentTypes, relationships, excelMain, PERFIL_XLSX);
        if (pacoteExcel != null
                && planilhaValida(pacoteExcel, excelRelationships, worksheets)) {
            return FormatoDocumento.XLSX;
        }
        return null;
    }

    private Map<String, String> validarDiretorioCentral(byte[] content) {
        Path arquivoTemporario = null;
        try {
            arquivoTemporario = Files.createTempFile("sicc-ooxml-", ".zip");
            Files.write(arquivoTemporario, content);
            try (ZipFile zip = new ZipFile(arquivoTemporario.toFile())) {
                Set<String> entryNames = new HashSet<>();
                Map<String, String> checksums = new HashMap<>();
                long[] uncompressedBytes = {0};
                int entries = 0;
                Enumeration<? extends ZipEntry> centralDirectory = zip.entries();
                while (centralDirectory.hasMoreElements()) {
                    if (++entries > MAX_OOXML_ENTRIES) {
                        throw new DomainException("Arquivo compactado inválido.");
                    }
                    ZipEntry entry = centralDirectory.nextElement();
                    String name = nomeEntradaValido(entry, entryNames);
                    try (InputStream input = zip.getInputStream(entry)) {
                        EntradaLida entradaLida = lerEntradaLimitada(
                                input,
                                MAX_OOXML_UNCOMPRESSED_BYTES,
                                uncompressedBytes,
                                false);
                        checksums.put(name, entradaLida.checksumSha256());
                    }
                }
                return checksums;
            }
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("Arquivo compactado inválido.");
        } finally {
            excluirArquivoTemporario(arquivoTemporario);
        }
    }

    private void excluirArquivoTemporario(Path arquivoTemporario) {
        if (arquivoTemporario == null) return;
        try {
            Files.deleteIfExists(arquivoTemporario);
        } catch (Exception ignored) {
            arquivoTemporario.toFile().deleteOnExit();
        }
    }

    private String nomeEntradaValido(ZipEntry entry, Set<String> entryNames) {
        String name = entry.getName();
        if (name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0) {
            throw new DomainException("Arquivo compactado inválido.");
        }
        String[] segments = name.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            boolean trailingDirectory = i == segments.length - 1
                    && segments[i].isEmpty()
                    && entry.isDirectory();
            if ((!trailingDirectory && segments[i].isEmpty())
                    || ".".equals(segments[i])
                    || "..".equals(segments[i])) {
                throw new DomainException("Arquivo compactado inválido.");
            }
        }
        String unambiguousName = (entry.isDirectory()
                        ? name.substring(0, name.length() - 1)
                        : name)
                .toLowerCase(Locale.ROOT);
        if (!entryNames.add(unambiguousName)) {
            throw new DomainException("Arquivo compactado inválido.");
        }
        return name;
    }

    private EntradaLida lerEntradaLimitada(
            InputStream zip, int limite, long[] totalDescompactado, boolean guardar)
            throws Exception {
        ByteArrayOutputStream output = guardar ? new ByteArrayOutputStream() : null;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[4096];
        int total = 0;
        int lidos;
        while ((lidos = zip.read(buffer)) != -1) {
            total += lidos;
            totalDescompactado[0] += lidos;
            if (total > limite || totalDescompactado[0] > MAX_OOXML_UNCOMPRESSED_BYTES) {
                throw new DomainException("Arquivo compactado inválido.");
            }
            digest.update(buffer, 0, lidos);
            if (guardar) output.write(buffer, 0, lidos);
        }
        return new EntradaLida(
                guardar ? output.toByteArray() : null,
                HexFormat.of().formatHex(digest.digest()));
    }

    private PacoteOoxml analisarPacoteOoxml(
            byte[] contentTypes,
            byte[] relationships,
            byte[] mainXml,
            PerfilOoxml perfil) {
        Document types = xml(contentTypes);
        Document rels = xml(relationships);
        Document main = xml(mainXml);
        boolean valido = types != null
                && rels != null
                && main != null
                && elementoRaiz(types, CONTENT_TYPES_NAMESPACE, "Types")
                && elementoRaiz(rels, RELATIONSHIPS_NAMESPACE, "Relationships")
                && declaraParte(types, perfil.nomeParte(), perfil.tipoConteudo())
                && relacionaPartePrincipal(rels, perfil.alvoRelacionamento())
                && elementoRaiz(main, perfil.namespacePrincipal(), perfil.elementoPrincipal())
                && filhoDireto(
                        main.getDocumentElement(),
                        perfil.namespacePrincipal(),
                        perfil.filhoPrincipalObrigatorio()) != null;
        return valido ? new PacoteOoxml(types, main) : null;
    }

    private boolean planilhaValida(
            PacoteOoxml pacote,
            byte[] relationshipsContent,
            Map<String, byte[]> worksheets) {
        if (relationshipsContent == null || worksheets.isEmpty()) return false;
        Document relationships = xml(relationshipsContent);
        if (relationships == null
                || !elementoRaiz(relationships, RELATIONSHIPS_NAMESPACE, "Relationships")) {
            return false;
        }
        Element sheets = filhoDireto(
                pacote.principal().getDocumentElement(), SPREADSHEET_NAMESPACE, "sheets");
        boolean encontrouPlanilha = false;
        for (Node child = sheets.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !SPREADSHEET_NAMESPACE.equals(child.getNamespaceURI())
                    || !"sheet".equals(child.getLocalName())) {
                continue;
            }
            encontrouPlanilha = true;
            Element sheet = (Element) child;
            String relationshipId = sheet.getAttributeNS(
                    OFFICE_DOCUMENT_RELATIONSHIPS_NAMESPACE, "id");
            Element relationship = relacionamentoPorId(relationships, relationshipId);
            if (relationship == null
                    || !WORKSHEET_RELATIONSHIP.equals(relationship.getAttribute("Type"))
                    || "External".equalsIgnoreCase(relationship.getAttribute("TargetMode"))) {
                return false;
            }
            String worksheetPath = caminhoWorksheet(relationship.getAttribute("Target"));
            byte[] worksheetContent = worksheetPath == null ? null : worksheets.get(worksheetPath);
            Document worksheet = worksheetContent == null ? null : xml(worksheetContent);
            if (worksheet == null
                    || !declaraParte(
                            pacote.tipos(), "/" + worksheetPath, WORKSHEET_CONTENT_TYPE)
                    || !elementoRaiz(worksheet, SPREADSHEET_NAMESPACE, "worksheet")
                    || filhoDireto(
                            worksheet.getDocumentElement(),
                            SPREADSHEET_NAMESPACE,
                            "sheetData") == null) {
                return false;
            }
        }
        return encontrouPlanilha;
    }

    private Element relacionamentoPorId(Document relationships, String id) {
        if (id == null || id.isBlank()) return null;
        Element root = relationships.getDocumentElement();
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && RELATIONSHIPS_NAMESPACE.equals(child.getNamespaceURI())
                    && "Relationship".equals(child.getLocalName())
                    && id.equals(((Element) child).getAttribute("Id"))) {
                return (Element) child;
            }
        }
        return null;
    }

    private String caminhoWorksheet(String target) {
        if (target == null || target.isBlank() || target.indexOf('\\') >= 0) return null;
        String path = target.startsWith("/") ? target.substring(1) : "xl/" + target;
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        return path.startsWith("xl/worksheets/") && path.endsWith(".xml") ? path : null;
    }

    private Document xml(byte[] content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(REJECTING_XML_ERROR_HANDLER);
            return builder.parse(new ByteArrayInputStream(content));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean declaraParte(Document document, String partName, String contentType) {
        Element root = document.getDocumentElement();
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !CONTENT_TYPES_NAMESPACE.equals(child.getNamespaceURI())
                    || !"Override".equals(child.getLocalName())) {
                continue;
            }
            Element override = (Element) child;
            if (partName.equals(override.getAttribute("PartName"))
                    && contentType.equals(override.getAttribute("ContentType"))) {
                return true;
            }
        }
        return false;
    }

    private boolean relacionaPartePrincipal(Document document, String target) {
        Element root = document.getDocumentElement();
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !RELATIONSHIPS_NAMESPACE.equals(child.getNamespaceURI())
                    || !"Relationship".equals(child.getLocalName())) {
                continue;
            }
            Element relationship = (Element) child;
            if (OFFICE_DOCUMENT_RELATIONSHIP.equals(relationship.getAttribute("Type"))
                    && target.equals(relationship.getAttribute("Target"))
                    && !"External".equalsIgnoreCase(relationship.getAttribute("TargetMode"))) {
                return true;
            }
        }
        return false;
    }

    private boolean elementoRaiz(Document document, String namespace, String localName) {
        Element root = document.getDocumentElement();
        return root != null
                && namespace.equals(root.getNamespaceURI())
                && localName.equals(root.getLocalName());
    }

    private Element filhoDireto(Element root, String namespace, String localName) {
        if (root == null) return null;
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && namespace.equals(child.getNamespaceURI())
                    && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private boolean csvValido(byte[] content) {
        if (content.length == 0) return false;
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            return false;
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\u0000' || (Character.isISOControl(current)
                    && current != '\r' && current != '\n' && current != '\t')) {
                return false;
            }
        }
        char delimiter = delimitadorCsv(text);
        return delimiter != 0 && estruturaCsvValida(text, delimiter);
    }

    private char delimitadorCsv(String text) {
        boolean quoted = false;
        int commas = 0;
        int semicolons = 0;
        int tabs = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && (current == '\r' || current == '\n')) {
                break;
            } else if (!quoted && current == ',') {
                commas++;
            } else if (!quoted && current == ';') {
                semicolons++;
            } else if (!quoted && current == '\t') {
                tabs++;
            }
        }
        if (commas == 0 && semicolons == 0 && tabs == 0) return 0;
        if (commas >= semicolons && commas >= tabs) return ',';
        return semicolons >= tabs ? ';' : '\t';
    }

    private boolean estruturaCsvValida(String text, char delimiter) {
        boolean quoted = false;
        boolean fieldStart = true;
        boolean rowHasContent = false;
        int columns = 1;
        int expectedColumns = -1;
        int rows = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (quoted) {
                if (current == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    i++;
                } else if (current == '"') {
                    quoted = false;
                }
                rowHasContent = true;
            } else if (current == '"' && fieldStart) {
                quoted = true;
                rowHasContent = true;
                fieldStart = false;
            } else if (current == '"') {
                return false;
            } else if (current == delimiter) {
                columns++;
                rowHasContent = true;
                fieldStart = true;
            } else if (current == '\r' || current == '\n') {
                if (current == '\r'
                        && i + 1 < text.length()
                        && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (rowHasContent) {
                    if (expectedColumns == -1) expectedColumns = columns;
                    if (columns != expectedColumns) return false;
                    rows++;
                }
                columns = 1;
                rowHasContent = false;
                fieldStart = true;
            } else {
                rowHasContent = true;
                fieldStart = false;
            }
        }
        if (quoted) return false;
        if (rowHasContent) {
            if (expectedColumns == -1) expectedColumns = columns;
            if (columns != expectedColumns) return false;
            rows++;
        }
        return rows > 0 && expectedColumns > 1;
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) return false;
        }
        return true;
    }

    private record PerfilOoxml(
            String nomeParte,
            String alvoRelacionamento,
            String tipoConteudo,
            String namespacePrincipal,
            String elementoPrincipal,
            String filhoPrincipalObrigatorio) {}

    private record PacoteOoxml(Document tipos, Document principal) {}

    private record EntradaLida(byte[] conteudo, String checksumSha256) {}
}
