package com.moments.sicc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RegrasDeVigenciaTest {
    private static final LocalDate HOJE = LocalDate.of(2026, 7, 27);
    private final RegrasDeVigencia regras = new RegrasDeVigencia(
            Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void consideraExatamenteCentoEVinteDiasComoProximoDoVencimento() {
        assertThat(regras.situacao(HOJE.plusDays(120))).isEqualTo(SituacaoVigencia.PROXIMA_VENCIMENTO);
    }

    @Test
    void consideraCentoEVinteEUmDiasComoVigenciaValida() {
        assertThat(regras.situacao(HOJE.plusDays(121))).isEqualTo(SituacaoVigencia.VALIDA);
    }

    @Test
    void consideraOProprioDiaFinalComoProximoDoVencimentoEEmVigencia() {
        assertThat(regras.situacao(HOJE)).isEqualTo(SituacaoVigencia.PROXIMA_VENCIMENTO);
        assertThat(regras.status(HOJE)).isEqualTo(StatusProcesso.EM_VIGENCIA);
    }

    @Test
    void distingueVigenciaVencidaEAusente() {
        assertThat(regras.situacao(HOJE.minusDays(1))).isEqualTo(SituacaoVigencia.VENCIDA);
        assertThat(regras.situacao(null)).isEqualTo(SituacaoVigencia.NAO_INFORMADA);
    }

    @Test
    void statusDependeSomenteDaVigenciaContratual() {
        assertThat(regras.status(null)).isEqualTo(StatusProcesso.EM_FORMALIZACAO);
        assertThat(regras.status(HOJE.plusDays(1))).isEqualTo(StatusProcesso.EM_VIGENCIA);
        assertThat(regras.status(HOJE.minusDays(1))).isEqualTo(StatusProcesso.CONCLUIDO);
    }
}
