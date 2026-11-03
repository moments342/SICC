package com.moments.sicc.service;

import static com.moments.sicc.domain.Enums.TipoRelatorio.ANUAL_PROCESSOS;
import static com.moments.sicc.domain.Enums.TipoRelatorio.INSTRUMENTOS_POR_TIPO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.domain.Enums.TipoRelatorio;
import com.moments.sicc.domain.Enums.FormatoRelatorio;
import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ConteudoExportadoRelatorioTest {
    private final InstrumentoContratualRepository instrumentos = mock(InstrumentoContratualRepository.class);
    private final LeituraTramitacao leitura = mock(LeituraTramitacao.class);
    private final GeracaoRelatorios geracao = new GeracaoRelatorios(instrumentos, leitura, new IndicadoresPortfolio(leitura));

    @Test
    void anualPermiteIdentificarOsProcessosQueCompoemOResumoDoAno() {
        ProcessoAdministrativo incluido = processo(1L, "PROC-2026-001", 2026);
        ProcessoAdministrativo excluido = processo(2L, "PROC-2025-002", 2025);

        String conteudo = texto(geracao.gerar(ANUAL_PROCESSOS,
                contexto(List.of(incluido, excluido), Map.of("ano", "2026"))));

        assertThat(conteudo).contains("PROC-2026-001", "DIPAC", "2026-01-10")
                .doesNotContain("PROC-2025-002");
    }

    @Test
    void instrumentosPorTipoIncluiOsInstrumentosAlemDaContagem() {
        ProcessoAdministrativo processo = processo(1L, "PROC-2026-001", 2026);
        InstrumentoContratual instrumento = new InstrumentoContratual();
        instrumento.setNumero("CV-001/2026");
        instrumento.setTipo(TipoInstrumento.CONVENIO);
        instrumento.setCoordenador("Maria da Silva");
        instrumento.setObjeto("Pesquisa e extensão universitária");
        instrumento.setValorAtual(new BigDecimal("1500.00"));
        instrumento.setVigenciaContratualFinal(LocalDate.of(2027, 12, 31));
        when(instrumentos.findByProcessoId(1L)).thenReturn(Optional.of(instrumento));

        assertThat(texto(geracao.gerar(INSTRUMENTOS_POR_TIPO, contexto(List.of(processo), Map.of()))))
                .contains("CV-001/2026", "PROC-2026-001", "Maria da Silva", "Pesquisa e extensão universitária");
    }

    @Test
    void pdfApresentaOConteudoComoRelatorioLegivelEmVezDeTextoDelimitado() throws Exception {
        var conteudo = geracao.gerar(ANUAL_PROCESSOS,
                contexto(List.of(processo(1L, "PROC-2026-001", 2026)), Map.of()));

        try (var pdf = Loader.loadPDF(new AdaptadorPdfRelatorio().codificar(conteudo))) {
            assertThat(new PDFTextStripper().getText(pdf))
                    .contains("Relatório anual de processos", "PROC-2026-001", "Página 1")
                    .doesNotContain("relatorio;", "ano;total;", "numero_processo;");
        }
    }

    @ParameterizedTest
    @EnumSource(TipoRelatorio.class)
    void selecaoVaziaExplicaAusenciaDeDados(TipoRelatorio tipo) {
        assertThat(texto(geracao.gerar(tipo, contexto(List.of(), Map.of()))))
                .contains("Nenhum registro encontrado para os filtros selecionados.");
    }

    @Test
    void pdfPreservaTextoLongoEIdentificaContinuacoesSemSairDasMargens() throws Exception {
        ProcessoAdministrativo processo = processo(1L, "PROC-EXTENSO-001", 2026);
        InstrumentoContratual instrumento = instrumento("CV-001/2026");
        String longo = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".repeat(80) + "FIM-DO-OBJETO";
        instrumento.setObjeto(longo);
        instrumento.setDescricao("Descrição completa no final do registro.");
        when(instrumentos.findByProcessoId(1L)).thenReturn(Optional.of(instrumento));
        byte[] arquivo = new AdaptadorPdfRelatorio().codificar(
                geracao.gerar(TipoRelatorio.CONSOLIDADO, contexto(List.of(processo), Map.of())));
        try (var pdf = Loader.loadPDF(arquivo)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            String extraido = new PDFTextStripper().getText(pdf);
            // As quebras não podem descartar caracteres do identificador ou de uma palavra longa.
            assertThat(extraido).contains("FIM-DO-OBJETO", "Descrição completa no final do registro.", "(continuação)");
            for (int pagina = 1; pagina <= pdf.getNumberOfPages(); pagina++) {
                assertThat(extraido).contains("Página " + pagina + " de " + pdf.getNumberOfPages());
            }
            PDFTextStripper limites = new PDFTextStripper() {
                @Override
                protected void writeString(String texto, List<TextPosition> posicoes) throws IOException {
                    for (TextPosition posicao : posicoes) {
                        assertThat(posicao.getXDirAdj()).isGreaterThanOrEqualTo(39);
                        assertThat(posicao.getXDirAdj() + posicao.getWidthDirAdj()).isLessThanOrEqualTo(557);
                        assertThat(posicao.getYDirAdj()).isBetween(25f, 815f);
                    }
                    super.writeString(texto, posicoes);
                }
            };
            limites.getText(pdf);
        }
    }

    @Test
    void exemplosUsamOMesmoConteudoCompletoNosTresFormatos() throws Exception {
        var processos = java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(id -> processo(id, "23005.0000%02d/2026-10".formatted(id), 2026)).toList();
        for (var processo : processos) {
            if (processo.getId() == 8) continue;
            var instrumento = instrumento("CV-%03d/2026".formatted(processo.getId()));
            instrumento.setTipo(processo.getId() % 2 == 0 ? TipoInstrumento.ACORDO_PARCERIA : TipoInstrumento.CONVENIO);
            if (processo.getId() == 7) instrumento.setVigenciaContratualFinal(LocalDate.of(2026, 8, 7));
            when(instrumentos.findByProcessoId(processo.getId())).thenReturn(Optional.of(instrumento));
        }
        var setor = new LeituraTramitacao.SetorLido(1L, "DIPAC", "Divisão de Projetos, Acordos e Convênios", true);
        var permanencia = new LeituraTramitacao.PermanenciaLida(1L, setor, LocalDate.of(2026, 8, 1), null, 7, true);
        when(leitura.setoresAtuais(org.mockito.ArgumentMatchers.any())).thenReturn(Map.of(1L, setor));
        when(leitura.historicos(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new LeituraTramitacao.HistoricoLido(1L,
                        new LeituraTramitacao.Contexto(ContextoTramitacao.FORMALIZACAO, 1L),
                        LocalDate.of(2026, 8, 8), setor,
                        List.of(new LeituraTramitacao.MovimentoLido(1L, LocalDate.of(2026, 8, 1), 1,
                                setor,
                                new LeituraTramitacao.AutorLido(1L, "Maria da Silva", "maria@sicc.test", "maria", null, true, false),
                                "Recebimento para análise do Instrumento Contratual.", null,
                                permanencia)), List.of(permanencia))));
        GeradorArquivoRelatorio formatos = new GeradorArquivoRelatorio(List.of(
                new AdaptadorCsvRelatorio(), new AdaptadorPdfRelatorio(), new AdaptadorXlsxRelatorio()));
        String pasta = System.getProperty("sicc.relatorio.preview-directory");
        if (pasta != null) Files.createDirectories(Path.of(pasta));
        for (TipoRelatorio tipo : TipoRelatorio.values()) {
            var conteudo = geracao.gerar(tipo, contexto(processos, Map.of()));
            for (FormatoRelatorio formato : FormatoRelatorio.values()) {
                byte[] bytes = formatos.gerar(formato, conteudo);
                assertThat(bytes).isNotEmpty();
                if (formato == FormatoRelatorio.PDF) {
                    try (var pdf = Loader.loadPDF(bytes)) {
                        assertThat(new PDFTextStripper().getText(pdf)).contains("23005.000001/2026-10");
                    }
                }
                if (pasta != null) Files.write(Path.of(pasta, tipo.name().toLowerCase(java.util.Locale.ROOT)
                        + "." + formato.name().toLowerCase(java.util.Locale.ROOT)), bytes);
            }
        }
    }

    private InstrumentoContratual instrumento(String numero) {
        var instrumento = new InstrumentoContratual();
        instrumento.setNumero(numero);
        instrumento.setTipo(TipoInstrumento.CONVENIO);
        instrumento.setCoordenador("Maria da Silva");
        instrumento.setObjeto("Pesquisa e extensão universitária para desenvolvimento regional.");
        instrumento.setDescricao("Acompanhamento de atividades acadêmicas e prestação de contas.");
        instrumento.setNatureza("Pesquisa e extensão");
        instrumento.setParticipes("Universidade e fundação de apoio");
        instrumento.setValorAtual(new BigDecimal("125000.50"));
        instrumento.setDataFormalizacao(LocalDate.of(2026, 2, 15));
        instrumento.setVigenciaContratualFinal(LocalDate.of(2026, 11, 30));
        instrumento.setVigenciaTedFinal(LocalDate.of(2027, 3, 31));
        return instrumento;
    }

    private ProcessoAdministrativo processo(long id, String numero, int ano) {
        ProcessoAdministrativo processo = new ProcessoAdministrativo();
        processo.setId(id);
        processo.setNumero(numero);
        processo.setOrigem("DIPAC");
        processo.setDataCadastro(LocalDate.of(ano, 1, 10));
        return processo;
    }

    private GeracaoRelatorios.Contexto contexto(List<ProcessoAdministrativo> processos, Map<String, String> filtros) {
        Clock relogio = Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC);
        UsuarioInterno autor = new UsuarioInterno();
        autor.setNome("Administrador");
        autor.setLogin("admin");
        return new GeracaoRelatorios.Contexto(filtros, processos,
                EmissaoRelatorio.iniciar(relogio, new RegrasDeVigencia(relogio), autor));
    }

    private String texto(ConteudoRelatorio conteudo) {
        return conteudo.linhas().stream().flatMap(linha -> linha.celulas().stream())
                .map(ConteudoRelatorio.Celula::valor).collect(java.util.stream.Collectors.joining("\n"));
    }
}
