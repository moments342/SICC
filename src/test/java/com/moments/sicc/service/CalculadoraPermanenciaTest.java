package com.moments.sicc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.IdentidadeSetor;
import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.Setor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalculadoraPermanenciaTest {

    private final CalculadoraPermanencia calculadora = new CalculadoraPermanencia();

    @Test
    void movimentacoesNoMesmoSetorNaoReiniciamAPermanencia() {
        LocalDate hoje = LocalDate.of(2026, 8, 10);
        Setor dipac = setor(1L, "DIPAC");
        Setor proap = setor(2L, "PROAP");

        var permanencias = calculadora.calcular(List.of(
                movimento(11L, dipac, hoje.minusDays(9), 1),
                movimento(12L, dipac, hoje.minusDays(7), 1),
                movimento(13L, proap, hoje.minusDays(5), 1)), hoje);

        assertThat(permanencias).containsExactly(
                new CalculadoraPermanencia.Periodo(
                        11L, dipac, hoje.minusDays(9), hoje.minusDays(5), 4, false),
                new CalculadoraPermanencia.Periodo(
                        13L, proap, hoje.minusDays(5), null, 5, true));
    }

    @Test
    void percursoSemMovimentacoesNaoCriaPermanencia() {
        assertThat(calculadora.calcular(List.of(), LocalDate.of(2026, 8, 10))).isEmpty();
    }

    private Setor setor(Long id, String sigla) {
        Setor setor = new Setor();
        setor.setId(id);
        setor.atualizarIdentidade(IdentidadeSetor.de(sigla, "Setor " + sigla));
        return setor;
    }

    private Movimentacao movimento(Long id, Setor setor, LocalDate data, int sequencia) {
        Movimentacao movimento = new Movimentacao(
                ContextoTramitacao.FORMALIZACAO,
                1L,
                data,
                sequencia,
                setor,
                null,
                null,
                LocalDateTime.of(2026, 8, 10, 12, 0));
        movimento.setId(id);
        return movimento;
    }
}
