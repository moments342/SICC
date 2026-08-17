package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegrasDeVigencia {
    public static final long DIAS_ALERTA = 120;
    private final Clock clock;

    public SituacaoVigencia situacao(LocalDate data) {
        if (data == null) return SituacaoVigencia.NAO_INFORMADA;
        LocalDate hoje = LocalDate.now(clock);
        if (data.isBefore(hoje)) return SituacaoVigencia.VENCIDA;
        return ChronoUnit.DAYS.between(hoje, data) <= DIAS_ALERTA
                ? SituacaoVigencia.PROXIMA_VENCIMENTO : SituacaoVigencia.VALIDA;
    }

    public StatusProcesso status(LocalDate vigenciaContratualFinal) {
        if (vigenciaContratualFinal == null) return StatusProcesso.EM_FORMALIZACAO;
        return vigenciaContratualFinal.isBefore(LocalDate.now(clock))
                ? StatusProcesso.CONCLUIDO : StatusProcesso.EM_VIGENCIA;
    }

    public boolean estaNoMarcoDeAlerta(LocalDate dataFinal) {
        return dataFinal != null
                && ChronoUnit.DAYS.between(LocalDate.now(clock), dataFinal) == DIAS_ALERTA;
    }
}
