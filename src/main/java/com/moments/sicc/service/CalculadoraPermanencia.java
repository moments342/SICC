package com.moments.sicc.service;

import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.Setor;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class CalculadoraPermanencia {

    public List<Periodo> calcular(
            List<Movimentacao> percurso, LocalDate dataReferencia) {
        Objects.requireNonNull(percurso);
        Objects.requireNonNull(dataReferencia);
        if (percurso.isEmpty()) return List.of();

        List<Periodo> permanencias = new ArrayList<>();
        Movimentacao chegada = percurso.getFirst();
        for (int indice = 1; indice < percurso.size(); indice++) {
            Movimentacao movimento = percurso.get(indice);
            if (Objects.equals(
                    chegada.getSetorDestino().getId(),
                    movimento.getSetorDestino().getId())) {
                continue;
            }
            permanencias.add(permanencia(chegada, movimento.getDataMovimentacao(), false));
            chegada = movimento;
        }
        permanencias.add(permanencia(chegada, dataReferencia, true));
        return List.copyOf(permanencias);
    }

    private Periodo permanencia(
            Movimentacao chegada, LocalDate fimPermanencia, boolean aberta) {
        return new Periodo(
                chegada.getId(),
                chegada.getSetorDestino(),
                chegada.getDataMovimentacao(),
                aberta ? null : fimPermanencia,
                Math.max(0, ChronoUnit.DAYS.between(
                        chegada.getDataMovimentacao(), fimPermanencia)),
                aberta);
    }

    public record Periodo(
            Long movimentacaoChegadaId,
            Setor setor,
            LocalDate dataChegada,
            LocalDate dataSaida,
            long diasCorridos,
            boolean aberta) {}
}
