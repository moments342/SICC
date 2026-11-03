package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.FormatoRelatorio;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
            List<ConteudoRelatorio.Celula> cabecalho = List.of();
            for (ConteudoRelatorio.Linha linha : conteudo.linhas()) {
                if (linha.papel() == ConteudoRelatorio.Papel.CABECALHO) cabecalho = linha.celulas();
                linhas.append("<row r=\"").append(numeroLinha++).append("\"");
                if (linha.papel() == ConteudoRelatorio.Papel.CABECALHO) linhas.append(" ht=\"45\" customHeight=\"1\"");
                linhas.append('>');
                for (int coluna = 0; coluna < linha.celulas().size(); coluna++) {
                    ConteudoRelatorio.Celula celula = linha.celulas().get(coluna);
                    String valor = celula.textual()
                            ? TextoCelulaRelatorio.emUmaCelula(celula.valor())
                            : celula.valor();
                    int estilo = switch (linha.papel()) {
                        case TITULO, SECAO -> 2;
                        case CABECALHO -> 1;
                        default -> 0;
                    };
                    if (celula.natureza() == ConteudoRelatorio.Celula.Natureza.NUMERO
                            || celula.natureza() == ConteudoRelatorio.Celula.Natureza.MOEDA
                            || celula.natureza() == ConteudoRelatorio.Celula.Natureza.PERCENTUAL) {
                        boolean monetario = celula.natureza() == ConteudoRelatorio.Celula.Natureza.MOEDA
                                || coluna < cabecalho.size() && cabecalho.get(coluna).valor().startsWith("valor");
                        boolean porcentagem = celula.natureza() == ConteudoRelatorio.Celula.Natureza.PERCENTUAL;
                        if (porcentagem) valor = new java.math.BigDecimal(valor).movePointLeft(2).toPlainString();
                        linhas.append("<c t=\"n\" s=\"").append(porcentagem ? 6 : monetario ? 3 : 0)
                                .append("\"><v>").append(valor).append("</v></c>");
                        continue;
                    }
                    if (linha.papel() == ConteudoRelatorio.Papel.DADOS
                            && (celula.natureza() == ConteudoRelatorio.Celula.Natureza.DATA
                            || celula.natureza() == ConteudoRelatorio.Celula.Natureza.DATA_HORA)) {
                        LocalDateTime data = valor.length() == 10 ? LocalDate.parse(valor).atStartOfDay() : LocalDateTime.parse(valor);
                        long dias = ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 31), data.toLocalDate());
                        if (!data.toLocalDate().isBefore(LocalDate.of(1900, 3, 1))) dias++;
                        double serial = dias + data.toLocalTime().toSecondOfDay() / 86400.0;
                        linhas.append("<c t=\"n\" s=\"").append(valor.length() == 10 ? 4 : 5)
                                .append("\"><v>").append(serial).append("</v></c>");
                        continue;
                    }
                    if (celula.natureza() == ConteudoRelatorio.Celula.Natureza.LOGICO) {
                        linhas.append("<c t=\"b\"><v>").append(Boolean.parseBoolean(valor) ? 1 : 0).append("</v></c>");
                        continue;
                    }
                    linhas.append("<c t=\"inlineStr\" s=\"").append(estilo).append("\"><is><t");
                    if (preservaEspacos(valor)) linhas.append(" xml:space=\"preserve\"");
                    linhas.append('>').append(xml(valor)).append("</t></is></c>");
                }
                linhas.append("</row>");
            }
            Map<String, String> entradas = new LinkedHashMap<>();
            entradas.put("[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            entradas.put("_rels/.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entradas.put("xl/workbook.xml", "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"SICC\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entradas.put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            entradas.put("xl/styles.xml", estilos());
            entradas.put("xl/worksheets/sheet1.xml", "<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"/></sheetViews><cols><col min=\"1\" max=\"1\" width=\"30\" customWidth=\"1\"/><col min=\"2\" max=\"19\" width=\"25\" customWidth=\"1\"/></cols><sheetData>" + linhas + "</sheetData></worksheet>");
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

    private String estilos() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <numFmts count="3"><numFmt numFmtId="164" formatCode="&quot;R$&quot; #,##0.00"/>
                <numFmt numFmtId="165" formatCode="dd/mm/yyyy"/>
                <numFmt numFmtId="166" formatCode="dd/mm/yyyy hh:mm"/></numFmts>
                <fonts count="3"><font><sz val="11"/><name val="Calibri"/></font>
                <font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font>
                <font><b/><sz val="13"/><name val="Calibri"/></font></fonts>
                <fills count="3"><fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF183349"/><bgColor indexed="64"/></patternFill></fill></fills>
                <borders count="1"><border/></borders>
                <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                <cellXfs count="7"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment wrapText="1" vertical="center"/></xf>
                <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
                <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                <xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                <xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                <xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
                <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                </styleSheet>
                """;
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
