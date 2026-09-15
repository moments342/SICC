package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegrasDeVigencia {
    public static final long DIAS_ALERTA = 120;
    private final Clock clock;

    public ReferenciaDeVigencia referenciaAtual() {
        return referenciaEm(LocalDate.now(clock));
    }

    public ReferenciaDeVigencia referenciaEm(LocalDate data) {
        return new ReferenciaDeVigencia(data);
    }

    public SituacaoVigencia situacao(LocalDate data) {
        return referenciaAtual().situacao(data);
    }

    public StatusProcesso status(LocalDate vigenciaContratualFinal) {
        return referenciaAtual().status(vigenciaContratualFinal);
    }

    public boolean estaNoMarcoDeAlerta(LocalDate dataFinal) {
        return referenciaAtual().estaNoMarcoDeAlerta(dataFinal);
    }

    public static final class ReferenciaDeVigencia {
        private final LocalDate hoje;
        private final Map<StatusProcesso, CondicaoDeData> condicoesDeStatus;
        private final Map<SituacaoVigencia, CondicaoDeData> condicoesDeSituacao;

        private ReferenciaDeVigencia(LocalDate hoje) {
            this.hoje = Objects.requireNonNull(hoje);
            LocalDate limiteDeProximidade = hoje.plusDays(DIAS_ALERTA);

            condicoesDeStatus = new EnumMap<>(StatusProcesso.class);
            condicoesDeStatus.put(StatusProcesso.EM_FORMALIZACAO, CondicaoDeData.ausente());
            condicoesDeStatus.put(StatusProcesso.EM_VIGENCIA, CondicaoDeData.emOuDepoisDe(hoje));
            condicoesDeStatus.put(StatusProcesso.CONCLUIDO, CondicaoDeData.antesDe(hoje));

            condicoesDeSituacao = new EnumMap<>(SituacaoVigencia.class);
            condicoesDeSituacao.put(SituacaoVigencia.NAO_INFORMADA, CondicaoDeData.ausente());
            condicoesDeSituacao.put(SituacaoVigencia.VENCIDA, CondicaoDeData.antesDe(hoje));
            condicoesDeSituacao.put(
                    SituacaoVigencia.PROXIMA_VENCIMENTO,
                    CondicaoDeData.entreInclusive(hoje, limiteDeProximidade));
            condicoesDeSituacao.put(
                    SituacaoVigencia.VALIDA,
                    CondicaoDeData.depoisDe(limiteDeProximidade));
        }

        public StatusProcesso status(LocalDate vigenciaContratualFinal) {
            return correspondente(vigenciaContratualFinal, condicoesDeStatus);
        }

        public SituacaoVigencia situacao(LocalDate dataFinal) {
            return correspondente(dataFinal, condicoesDeSituacao);
        }

        public CondicaoDeData condicao(StatusProcesso status) {
            return Objects.requireNonNull(condicoesDeStatus.get(status));
        }

        public CondicaoDeData condicao(SituacaoVigencia situacao) {
            return Objects.requireNonNull(condicoesDeSituacao.get(situacao));
        }

        public boolean estaNoMarcoDeAlerta(LocalDate dataFinal) {
            return dataFinal != null
                    && ChronoUnit.DAYS.between(hoje, dataFinal) == DIAS_ALERTA;
        }

        private <E extends Enum<E>> E correspondente(
                LocalDate data, Map<E, CondicaoDeData> condicoes) {
            return condicoes.entrySet().stream()
                    .filter(entrada -> entrada.getValue().corresponde(data))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "As regras de vigência não cobrem a data informada."));
        }
    }

    public record CondicaoDeData(
            boolean somenteAusente,
            LocalDate inicioInclusivo,
            LocalDate fimExclusivo) {

        public CondicaoDeData {
            if (somenteAusente && (inicioInclusivo != null || fimExclusivo != null)) {
                throw new IllegalArgumentException(
                        "Uma condição de ausência não possui limites de data.");
            }
            if (!somenteAusente && inicioInclusivo == null && fimExclusivo == null) {
                throw new IllegalArgumentException(
                        "Uma condição de data precisa de ao menos um limite.");
            }
            if (inicioInclusivo != null
                    && fimExclusivo != null
                    && !inicioInclusivo.isBefore(fimExclusivo)) {
                throw new IllegalArgumentException(
                        "O início da condição deve anteceder o fim.");
            }
        }

        private static CondicaoDeData ausente() {
            return new CondicaoDeData(true, null, null);
        }

        private static CondicaoDeData antesDe(LocalDate limite) {
            return new CondicaoDeData(false, null, limite);
        }

        private static CondicaoDeData emOuDepoisDe(LocalDate limite) {
            return new CondicaoDeData(false, limite, null);
        }

        private static CondicaoDeData entreInclusive(LocalDate inicio, LocalDate fim) {
            return new CondicaoDeData(false, inicio, fim.plusDays(1));
        }

        private static CondicaoDeData depoisDe(LocalDate limite) {
            return new CondicaoDeData(false, limite.plusDays(1), null);
        }

        public boolean corresponde(LocalDate data) {
            if (data == null || somenteAusente) return data == null && somenteAusente;
            return (inicioInclusivo == null || !data.isBefore(inicioInclusivo))
                    && (fimExclusivo == null || data.isBefore(fimExclusivo));
        }
    }
}
