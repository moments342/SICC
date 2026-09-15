package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.FormatoRelatorio;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

@Component
final class AdaptadorXlsxRelatorio implements AdaptadorFormatoRelatorio {
    @Override
    public FormatoRelatorio formato() {
        return FormatoRelatorio.XLSX;
    }

    @Override
    public String mime() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public byte[] codificar(ConteudoRelatorio conteudo) {
        try {
            StringBuilder linhas = new StringBuilder();
            int numeroLinha = 1;
            for (ConteudoRelatorio.Linha linha : conteudo.linhas()) {
                linhas.append("<row r=\"").append(numeroLinha++).append("\">");
                for (ConteudoRelatorio.Celula celula : linha.celulas()) {
                    String valor = celula.textual()
                            ? TextoCelulaRelatorio.emUmaCelula(celula.valor())
                            : celula.valor();
                    linhas.append("<c t=\"inlineStr\"><is><t");
                    if (preservaEspacos(valor)) linhas.append(" xml:space=\"preserve\"");
                    linhas.append('>').append(xml(valor)).append("</t></is></c>");
                }
                linhas.append("</row>");
            }
            Map<String, String> entradas = new LinkedHashMap<>();
            entradas.put("[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            entradas.put("_rels/.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entradas.put("xl/workbook.xml", "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"SICC\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entradas.put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            entradas.put("xl/worksheets/sheet1.xml", "<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>" + linhas + "</sheetData></worksheet>");
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(saida)) {
                for (var entrada : entradas.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entrada.getKey()));
                    zip.write(entrada.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return saida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar XLSX.", e);
        }
    }

    private boolean preservaEspacos(String valor) {
        return !valor.isEmpty()
                && (TextoCelulaRelatorio.eEspacoUnicode(valor.charAt(0))
                        || TextoCelulaRelatorio.eEspacoUnicode(
                                valor.charAt(valor.length() - 1)));
    }

    private String xml(String valor) {
        return valor.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
