package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.FormatoRelatorio;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

@Component
final class AdaptadorPdfRelatorio implements AdaptadorFormatoRelatorio {
    @Override
    public FormatoRelatorio formato() {
        return FormatoRelatorio.PDF;
    }

    @Override
    public String mime() {
        return "application/pdf";
    }

    @Override
    public byte[] codificar(ConteudoRelatorio conteudo) {
        try (PDDocument documento = new PDDocument();
             InputStream fonteStream = Files.newInputStream(localizarFonteUnicode())) {
            PDType0Font fonte = PDType0Font.load(documento, fonteStream, true);
            List<String> linhas = linhasPdf(conteudo);
            for (int inicio = 0; inicio < linhas.size(); inicio += 72) {
                PDPage pagina = new PDPage(PDRectangle.A4);
                documento.addPage(pagina);
                try (PDPageContentStream paginaConteudo =
                             new PDPageContentStream(documento, pagina)) {
                    paginaConteudo.beginText();
                    paginaConteudo.setFont(fonte, 8);
                    paginaConteudo.newLineAtOffset(40, 800);
                    for (String linha : linhas.subList(
                            inicio, Math.min(inicio + 72, linhas.size()))) {
                        paginaConteudo.showText(linha);
                        paginaConteudo.newLineAtOffset(0, -10);
                    }
                    paginaConteudo.endText();
                }
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            documento.save(saida);
            return saida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o PDF do relatório.", e);
        }
    }

    private Path localizarFonteUnicode() {
        List<Path> candidatas = new ArrayList<>();
        String configurada = System.getProperty("sicc.relatorio.font-path");
        if (configurada != null && !configurada.isBlank()) candidatas.add(Path.of(configurada));
        String windows = System.getenv("WINDIR");
        if (windows != null && !windows.isBlank()) {
            candidatas.add(Path.of(windows, "Fonts", "DejaVuSans.ttf"));
            candidatas.add(Path.of(windows, "Fonts", "arial.ttf"));
        }
        candidatas.add(Path.of(
                System.getProperty("java.home"), "lib", "fonts", "DejaVuSans.ttf"));
        candidatas.add(Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
        candidatas.add(Path.of(
                "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"));
        candidatas.add(Path.of("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"));
        return candidatas.stream().filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Fonte TrueType Unicode não encontrada; "
                                + "configure sicc.relatorio.font-path."));
    }

    private List<String> linhasPdf(ConteudoRelatorio conteudo) {
        List<String> linhas = new ArrayList<>();
        for (ConteudoRelatorio.Linha linha : conteudo.linhas()) {
            String original = linha.celulas().stream()
                    .map(celula -> celula.textual()
                            ? TextoCelulaRelatorio.emUmaCelula(celula.valor())
                            : celula.valor())
                    .reduce((esquerda, direita) -> esquerda + ";" + direita)
                    .orElse("");
            String restante = original;
            do {
                if (restante.length() <= 110) {
                    linhas.add(restante);
                    restante = "";
                } else {
                    int quebra = Math.max(
                            restante.lastIndexOf(';', 110),
                            restante.lastIndexOf(' ', 110));
                    if (quebra < 50) quebra = 110;
                    boolean separadorDescartavel = restante.charAt(quebra) == ' ';
                    linhas.add(restante.substring(
                            0, quebra + (separadorDescartavel ? 0 : 1)));
                    restante = restante.substring(quebra + 1).stripLeading();
                }
            } while (!restante.isEmpty());
        }
        return linhas;
    }
}
