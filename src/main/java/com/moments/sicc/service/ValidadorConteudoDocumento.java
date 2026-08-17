package com.moments.sicc.service;

import com.moments.sicc.shared.exception.DomainException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class ValidadorConteudoDocumento {
    private static final int MAX_CONTENT_TYPES_BYTES = 64 * 1024;
    private static final String DOCX_MAIN_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String XLSX_MAIN_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";

    public FormatoDocumento detectar(byte[] content) {
        if (pdfValido(content)) return FormatoDocumento.PDF;
        FormatoDocumento ooxml = detectarOoxml(content);
        if (ooxml != null) return ooxml;
        if (csvValido(content)) return FormatoDocumento.CSV;
        throw new DomainException("Formato real não permitido. Use PDF, DOCX, XLSX ou CSV.");
    }

    private boolean pdfValido(byte[] content) {
        if (content.length < 12
                || !startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }
        boolean versionSupported = content[6] == '.'
                && ((content[5] == '1' && content[7] >= '0' && content[7] <= '7')
                || (content[5] == '2' && content[7] == '0'));
        if (!versionSupported) return false;
        int trailerStart = Math.max(0, content.length - 1024);
        String trailer = new String(
                content,
                trailerStart,
                content.length - trailerStart,
                StandardCharsets.ISO_8859_1);
        return trailer.contains("%%EOF");
    }

    private FormatoDocumento detectarOoxml(byte[] content) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') return null;
        boolean relationships = false;
        boolean wordMain = false;
        boolean excelMain = false;
        String contentTypes = null;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 10_000) {
                    throw new DomainException("Arquivo compactado inválido.");
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../")) {
                    throw new DomainException("Arquivo compactado inválido.");
                }
                if ("[Content_Types].xml".equals(name)) {
                    contentTypes = new String(
                            lerEntradaLimitada(zip, MAX_CONTENT_TYPES_BYTES),
                            StandardCharsets.UTF_8);
                } else if ("_rels/.rels".equals(name)) {
                    relationships = true;
                } else if ("word/document.xml".equals(name)) {
                    wordMain = true;
                } else if ("xl/workbook.xml".equals(name)) {
                    excelMain = true;
                }
            }
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("Arquivo compactado inválido.");
        }
        if (!relationships || contentTypes == null || wordMain == excelMain) return null;
        if (wordMain && contentTypes.contains(DOCX_MAIN_CONTENT_TYPE)) {
            return FormatoDocumento.DOCX;
        }
        if (excelMain && contentTypes.contains(XLSX_MAIN_CONTENT_TYPE)) {
            return FormatoDocumento.XLSX;
        }
        return null;
    }

    private byte[] lerEntradaLimitada(ZipInputStream zip, int limite) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int lidos;
        while ((lidos = zip.read(buffer)) != -1) {
            total += lidos;
            if (total > limite) throw new DomainException("Arquivo compactado inválido.");
            output.write(buffer, 0, lidos);
        }
        return output.toByteArray();
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
}
