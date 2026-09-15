package com.moments.sicc.service;

import static com.moments.sicc.domain.Enums.TipoRelatorio.ANUAL_PROCESSOS;
import static com.moments.sicc.domain.Enums.TipoRelatorio.CONSOLIDADO;
import static com.moments.sicc.domain.Enums.TipoRelatorio.HISTORICO_TRAMITACOES;
import static com.moments.sicc.domain.Enums.TipoRelatorio.INSTRUMENTOS_POR_TIPO;
import static com.moments.sicc.domain.Enums.TipoRelatorio.VIGENCIAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.MovimentacaoRepository;
import com.moments.sicc.service.GeracaoRelatorios.Contexto;
import com.moments.sicc.domain.UsuarioInterno;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeracaoRelatoriosTest {
    private final MovimentacaoRepository movimentacoes = mock(MovimentacaoRepository.class);
    private final GeracaoRelatorios geracao = new GeracaoRelatorios(
            mock(InstrumentoContratualRepository.class),
            mock(AlteracaoContratualRepository.class),
            movimentacoes,
            new CalculadoraPermanencia());

    @Test
    void associaTituloFiltrosEGeradorNaMesmaDefinicao() {
        when(movimentacoes.findAll()).thenReturn(List.of());
        Clock relogio = Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC);
        UsuarioInterno autor = new UsuarioInterno();
        autor.setNome("Administrador");
        autor.setLogin("admin");
        EmissaoRelatorio emissao = EmissaoRelatorio.iniciar(
                relogio, new RegrasDeVigencia(relogio), autor);
        Contexto contexto = new Contexto(
                Map.of(), List.of(), emissao);

        assertThat(geracao.titulo(ANUAL_PROCESSOS))
                .isEqualTo("Relatório anual de processos");
        assertThat(geracao.filtrosPermitidos(ANUAL_PROCESSOS)).contains("ano");
        assertThat(celulas(geracao.gerar(ANUAL_PROCESSOS, contexto), 5))
                .startsWith("ano", "total");

        assertThat(geracao.titulo(HISTORICO_TRAMITACOES))
                .isEqualTo("Relatório do histórico de tramitações");
        assertThat(geracao.filtrosPermitidos(HISTORICO_TRAMITACOES))
                .contains("contexto", "dataInicial", "dataFinal");
        assertThat(celulas(geracao.gerar(HISTORICO_TRAMITACOES, contexto), 5))
                .startsWith("numero_processo", "contexto");

        assertThat(celulas(geracao.gerar(INSTRUMENTOS_POR_TIPO, contexto), 5))
                .startsWith("tipo_instrumento", "quantidade");
        assertThat(celulas(geracao.gerar(VIGENCIAS, contexto), 5))
                .startsWith("numero_processo", "tipo_instrumento", "vigencia_contratual");
        assertThat(celulas(geracao.gerar(CONSOLIDADO, contexto), 5))
                .startsWith("numero_processo", "origem", "status");
    }

    private List<String> celulas(ConteudoRelatorio conteudo, int linha) {
        return conteudo.linhas().get(linha).celulas().stream()
                .map(ConteudoRelatorio.Celula::valor)
                .toList();
    }
}
