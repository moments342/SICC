package com.moments.sicc.service;

import static com.moments.sicc.domain.Enums.FormatoRelatorio.CSV;
import static com.moments.sicc.domain.Enums.FormatoRelatorio.PDF;
import static com.moments.sicc.domain.Enums.FormatoRelatorio.XLSX;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class GeradorArquivoRelatorioTest {

    private final GeradorArquivoRelatorio gerador = new GeradorArquivoRelatorio(List.of(
            new AdaptadorCsvRelatorio(),
            new AdaptadorPdfRelatorio(),
            new AdaptadorXlsxRelatorio()));

    @Test
    void codificaAsMesmasCelulasNosTresFormatosSemProtocoloDelimitado() throws Exception {
        ConteudoRelatorio conteudo = ConteudoRelatorio.construtor()
                .linha(ConteudoRelatorio.Celula.texto("cabecalho"),
                        ConteudoRelatorio.Celula.texto("valor"))
                .linha(ConteudoRelatorio.Celula.texto("linha;um"),
                        ConteudoRelatorio.Celula.texto("<seguro>&"))
                .linha(ConteudoRelatorio.Celula.texto("formula"),
                        ConteudoRelatorio.Celula.texto("\u2007=2+3"))
                .linha(ConteudoRelatorio.Celula.texto("dias"),
                        ConteudoRelatorio.Celula.numero(-1))
                .linha(ConteudoRelatorio.Celula.texto("texto negativo"),
                        ConteudoRelatorio.Celula.texto("-1"))
                .construir();

        String csv = new String(gerador.gerar(CSV, conteudo), StandardCharsets.UTF_8);
        String pdf;
        try (var documento = Loader.loadPDF(gerador.gerar(PDF, conteudo))) {
            pdf = new PDFTextStripper().getText(documento);
        }
        String xlsx = primeiraPlanilha(gerador.gerar(XLSX, conteudo));

        assertThat(csv)
                .isEqualTo("cabecalho;valor\nlinha,um;<seguro>&\nformula;'\u2007=2+3\n"
                        + "dias;-1\ntexto negativo;'-1\n")
                .doesNotContain("dias;'-1");
        assertThat(pdf)
                .contains("cabecalho;valor", "linha,um;<seguro>&", "formula;\u2007=2+3");
        assertThat(xlsx)
                .contains("<t>linha,um</t>", "<t>&lt;seguro&gt;&amp;</t>",
                        "<t xml:space=\"preserve\">\u2007=2+3</t>")
                .doesNotContain("'\u2007=2+3");
        assertThat(quantidadeCelulas(xlsx)).isEqualTo(10);
    }

    private int quantidadeCelulas(String planilha) {
        return planilha.split("<c t=\"inlineStr\">", -1).length - 1;
    }

    @Test
    void centralizaOsTiposMimeDosFormatosSuportados() {
        assertThat(gerador.mime(CSV)).isEqualTo("text/csv;charset=UTF-8");
        assertThat(gerador.mime(PDF)).isEqualTo("application/pdf");
        assertThat(gerador.mime(XLSX)).isEqualTo(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @Test
    void csvNeutralizaFormulaAposTodoEspacoUnicodePelaInterfaceDoModule() {
        int[] espacos = {
            0x0009, 0x000a, 0x000b, 0x000c, 0x000d, 0x0020, 0x0085, 0x00a0,
            0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006,
            0x2007, 0x2008, 0x2009, 0x200a, 0x2028, 0x2029, 0x202f, 0x205f,
            0x3000
        };

        for (String formula : List.of("=1+1", "+SUM(A1:A2)", "-10+20", "@IMPORTDATA(A1)")) {
            for (int espaco : espacos) {
                String valor = Character.toString(espaco) + formula;
                ConteudoRelatorio conteudo = ConteudoRelatorio.construtor()
                        .linha(ConteudoRelatorio.Celula.texto(valor))
                        .construir();

                assertThat(new String(gerador.gerar(CSV, conteudo), StandardCharsets.UTF_8))
                        .as("CSV %s após U+%04X", formula, espaco)
                        .startsWith("'");
            }
        }
    }

    private String primeiraPlanilha(byte[] arquivo) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(arquivo))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("xl/worksheets/sheet1.xml")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("XLSX sem a primeira planilha.");
    }
}
