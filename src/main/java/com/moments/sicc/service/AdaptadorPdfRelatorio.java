package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.FormatoRelatorio;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    public FormatoRelatorio formato() { return FormatoRelatorio.PDF; }

    @Override
    public String mime() { return "application/pdf"; }

    @Override
    public byte[] codificar(ConteudoRelatorio conteudo) {
        try (PDDocument documento = new PDDocument();
             InputStream fonteStream = Files.newInputStream(localizarFonteUnicode())) {
            PDType0Font fonte = PDType0Font.load(documento, fonteStream, true);
            String titulo = conteudo.linhas().stream()
                    .filter(linha -> linha.papel() == ConteudoRelatorio.Papel.TITULO)
                    .map(linha -> linha.celulas().getLast().valor()).findFirst().orElse("Relatório SICC");
            documento.getDocumentInformation().setTitle(titulo);
            try (Diagrama diagrama = new Diagrama(documento, fonte, titulo)) {
                List<ConteudoRelatorio.Celula> cabecalho = List.of();
                for (var linha : conteudo.linhas()) {
                    switch (linha.papel()) {
                        case TITULO -> diagrama.paragrafo(titulo, 19, 10);
                        case METADADO -> diagrama.paragrafo(
                                ApresentacaoRelatorio.rotulo(linha.celulas().getFirst().valor()) + ": "
                                        + ApresentacaoRelatorio.valor(linha.celulas().getLast(), ""), 9, 4);
                        case NOTA -> diagrama.paragrafo(linha.celulas().getFirst().valor(), 9, 8);
                        case SECAO -> {
                            cabecalho = List.of();
                            diagrama.secao(linha.celulas().getFirst().valor());
                        }
                        case CABECALHO -> {
                            cabecalho = linha.celulas();
                            if (cabecalho.size() <= 6) diagrama.tabela(cabecalho, cabecalho, true);
                        }
                        case DADOS -> {
                            if (linha.celulas().isEmpty()) diagrama.espaco(10);
                            else if (cabecalho.isEmpty()) {
                                for (var celula : linha.celulas()) {
                                    diagrama.paragrafo(ApresentacaoRelatorio.valor(celula, ""), 10, 4);
                                }
                            } else if (cabecalho.size() <= 6) {
                                diagrama.tabela(linha.celulas(), cabecalho, false);
                            } else diagrama.registro(linha.celulas(), cabecalho);
                        }
                    }
                }
            }
            for (int indice = 0; indice < documento.getNumberOfPages(); indice++) {
                try (var rodape = new PDPageContentStream(documento, documento.getPage(indice),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    rodape.setNonStrokingColor(new Color(85, 95, 110));
                    rodape.beginText();
                    rodape.setFont(fonte, 8);
                    rodape.newLineAtOffset(40, 28);
                    rodape.showText("SICC - DIPAC");
                    rodape.newLineAtOffset(390, 0);
                    rodape.showText("Página " + (indice + 1) + " de " + documento.getNumberOfPages());
                    rodape.endText();
                }
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            documento.save(saida);
            return saida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o PDF do relatório.", e);
        }
    }

    private static final class Diagrama implements AutoCloseable {
        private static final float MARGEM = 40;
        private static final float LARGURA = PDRectangle.A4.getWidth() - MARGEM * 2;
        private static final float FUNDO = 52;
        private static final Color AZUL = new Color(24, 51, 73);
        private final PDDocument documento;
        private final PDType0Font fonte;
        private final String titulo;
        private PDPageContentStream pagina;
        private float y;

        Diagrama(PDDocument documento, PDType0Font fonte, String titulo) throws IOException {
            this.documento = documento;
            this.fonte = fonte;
            this.titulo = titulo;
            novaPagina();
        }

        private void novaPagina() throws IOException {
            if (pagina != null) pagina.close();
            PDPage folha = new PDPage(PDRectangle.A4);
            documento.addPage(folha);
            pagina = new PDPageContentStream(documento, folha);
            texto("SICC / DIPAC", MARGEM, 807, 9, AZUL);
            if (documento.getNumberOfPages() > 1) texto(titulo, MARGEM, 787, 11, AZUL);
            y = 762;
        }

        private boolean reservar(float altura) throws IOException {
            if (y - altura >= FUNDO) return false;
            novaPagina();
            return true;
        }

        void espaco(float altura) { y -= altura; }

        void paragrafo(String valor, float tamanho, float depois) throws IOException {
            for (String linha : quebrar(valor, LARGURA, tamanho)) {
                reservar(tamanho * 1.45f);
                texto(linha, MARGEM, y, tamanho, AZUL);
                y -= tamanho * 1.45f;
            }
            y -= depois;
        }

        void secao(String nome) throws IOException {
            reservar(90);
            y -= 12;
            paragrafo(nome, 13, 9);
        }

        void tabela(List<ConteudoRelatorio.Celula> celulas,
                    List<ConteudoRelatorio.Celula> cabecalho, boolean tituloTabela) throws IOException {
            float larguraColuna = LARGURA / cabecalho.size();
            List<List<String>> linhas = new ArrayList<>();
            for (int coluna = 0; coluna < celulas.size(); coluna++) {
                String valor = tituloTabela ? ApresentacaoRelatorio.rotulo(celulas.get(coluna).valor())
                        : ApresentacaoRelatorio.valor(celulas.get(coluna), cabecalho.get(coluna).valor());
                linhas.add(quebrar(valor, larguraColuna - 12, 9));
            }
            float altura = linhas.stream().mapToInt(List::size).max().orElse(1) * 13 + 14;
            if (reservar(altura + (tituloTabela ? 42 : 0)) && !tituloTabela) {
                tabela(cabecalho, cabecalho, true);
            }
            pagina.setNonStrokingColor(tituloTabela ? AZUL : new Color(242, 245, 247));
            pagina.addRect(MARGEM, y - altura + 8, LARGURA, altura);
            pagina.fill();
            for (int coluna = 0; coluna < linhas.size(); coluna++) {
                float posicaoY = y - 6;
                for (String linha : linhas.get(coluna)) {
                    texto(linha, MARGEM + coluna * larguraColuna + 6, posicaoY, 9,
                            tituloTabela ? Color.WHITE : AZUL);
                    posicaoY -= 13;
                }
            }
            y -= altura + 2;
        }

        void registro(List<ConteudoRelatorio.Celula> celulas,
                      List<ConteudoRelatorio.Celula> cabecalho) throws IOException {
            String identificacao = ApresentacaoRelatorio.valor(celulas.getFirst(), cabecalho.getFirst().valor());
            String tituloRegistro = ApresentacaoRelatorio.rotulo(cabecalho.getFirst().valor()) + ": " + identificacao;
            List<List<String>> campos = new ArrayList<>();
            for (int indice = 1; indice < celulas.size(); indice++) {
                String coluna = cabecalho.get(indice).valor();
                String valor = ApresentacaoRelatorio.valor(celulas.get(indice), coluna);
                campos.add(quebrar(ApresentacaoRelatorio.rotulo(coluna) + ": " + valor, LARGURA - 16, 9));
            }
            float altura = quebrar(tituloRegistro, LARGURA, 12).size() * 17.4f + 4
                    + campos.stream().mapToInt(List::size).sum() * 14 + 26;
            // Fichas que cabem em uma página começam inteiras; textos maiores continuam identificados.
            reservar(altura <= 710 ? altura : 100);
            paragrafo(tituloRegistro, 12, 4);
            for (List<String> linhas : campos) {
                for (String linha : linhas) {
                    if (reservar(14)) {
                        paragrafo(identificacao + " (continuação)", 11, 6);
                    }
                    texto(linha, MARGEM + 8, y, 9, AZUL);
                    y -= 14;
                }
            }
            y -= 8;
            pagina.setStrokingColor(new Color(209, 219, 226));
            pagina.moveTo(MARGEM, y);
            pagina.lineTo(MARGEM + LARGURA, y);
            pagina.stroke();
            y -= 18;
        }

        private void texto(String valor, float x, float posicaoY, float tamanho, Color cor) throws IOException {
            pagina.setNonStrokingColor(cor);
            pagina.beginText();
            pagina.setFont(fonte, tamanho);
            pagina.newLineAtOffset(x, posicaoY);
            pagina.showText(valor);
            pagina.endText();
        }

        private List<String> quebrar(String texto, float largura, float tamanho) throws IOException {
            List<String> linhas = new ArrayList<>();
            StringBuilder atual = new StringBuilder();
            int ultimaQuebra = -1;
            for (int posicao = 0; posicao < texto.length();) {
                int codigo = texto.codePointAt(posicao);
                posicao += Character.charCount(codigo);
                String caractere = new String(Character.toChars(codigo));
                try { fonte.encode(caractere); }
                catch (IllegalArgumentException e) { caractere = "?"; }
                if (fonte.getStringWidth(atual + caractere) * tamanho / 1000 > largura && !atual.isEmpty()) {
                    if (ultimaQuebra >= 0) {
                        linhas.add(atual.substring(0, ultimaQuebra));
                        atual.delete(0, ultimaQuebra + 1);
                    } else {
                        linhas.add(atual.toString());
                        atual.setLength(0);
                    }
                    ultimaQuebra = atual.lastIndexOf(" ");
                }
                if (caractere.equals(" ")) ultimaQuebra = atual.length();
                atual.append(caractere);
            }
            if (!atual.isEmpty() || linhas.isEmpty()) linhas.add(atual.toString());
            return linhas;
        }

        @Override
        public void close() throws IOException { pagina.close(); }
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
        candidatas.add(Path.of(System.getProperty("java.home"), "lib", "fonts", "DejaVuSans.ttf"));
        candidatas.add(Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
        candidatas.add(Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"));
        candidatas.add(Path.of("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"));
        return candidatas.stream().filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Fonte TrueType Unicode não encontrada; configure sicc.relatorio.font-path."));
    }
}
