package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.service.RegrasDeVigencia.ReferenciaDeVigencia;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Mesmas formulas e referencia temporal para a tela gerencial e as exportacoes. */
@Component
@RequiredArgsConstructor
public class IndicadoresPortfolio {
    private final LeituraTramitacao leituraTramitacao;

    public DashboardResponse calcular(List<ProcessoAdministrativo> selecionados,
            Map<Long, InstrumentoContratual> instrumentoPorProcesso,
            LocalDate hoje, ReferenciaDeVigencia referencia) {
        List<ItemPortfolio> itens = selecionados.stream()
                .map(processo -> {
                    InstrumentoContratual instrumento = instrumentoPorProcesso.get(processo.getId());
                    return new ItemPortfolio(
                            processo, instrumento, referencia.status(
                                    instrumento == null
                                            ? null : instrumento.getVigenciaContratualFinal()));
                })
                .toList();
        List<ProcessoAdministrativo> ativos = itens.stream()
                .map(ItemPortfolio::processo).toList();
        List<InstrumentoContratual> portfolio = itens.stream()
                .map(ItemPortfolio::instrumento)
                .filter(java.util.Objects::nonNull)
                .toList();
        EnumMap<StatusProcesso, Long> porStatus = new EnumMap<>(StatusProcesso.class);
        for (StatusProcesso status : StatusProcesso.values()) porStatus.put(status, 0L);
        itens.forEach(item -> porStatus.merge(item.status(), 1L, Long::sum));
        long total = ativos.size();
        double percentual = total == 0 ? 0 : porStatus.get(StatusProcesso.CONCLUIDO) * 100.0 / total;
        long alertasContrato = portfolio.stream()
                .filter(i -> referencia.situacao(i.getVigenciaContratualFinal())
                        == SituacaoVigencia.PROXIMA_VENCIMENTO)
                .count();
        long alertasTed = portfolio.stream()
                .filter(i -> referencia.situacao(i.getVigenciaTedFinal())
                        == SituacaoVigencia.PROXIMA_VENCIMENTO)
                .count();
        var valor = portfolio.stream()
                .filter(i -> !i.getVigenciaContratualFinal().isBefore(hoje))
                .map(InstrumentoContratual::getValorAtual)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        EnumMap<TipoInstrumento, Long> porTipo = new EnumMap<>(TipoInstrumento.class);
        for (TipoInstrumento tipoInstrumento : TipoInstrumento.values()) {
            porTipo.put(tipoInstrumento, 0L);
        }
        portfolio.forEach(i -> porTipo.merge(i.getTipo(), 1L, Long::sum));
        Permanencia permanencia = calcularPermanencias(ativos, hoje);
        List<TempoTramitacaoInicialProcessoResponse> detalhesTempoInicial = ativos.stream()
                .map(processo -> {
                    InstrumentoContratual instrumento = instrumentoPorProcesso.get(processo.getId());
                    LocalDate dataFormalizacao = instrumento == null
                            ? null : instrumento.getDataFormalizacao();
                    LocalDate fim = dataFormalizacao == null ? hoje : dataFormalizacao;
                    return new TempoTramitacaoInicialProcessoResponse(
                            processo.getId(), processo.getNumero(), processo.getDataCadastro(),
                            dataFormalizacao,
                            Math.max(0, ChronoUnit.DAYS.between(processo.getDataCadastro(), fim)),
                            dataFormalizacao == null);
                })
                .sorted(Comparator.comparing(TempoTramitacaoInicialProcessoResponse::numeroProcesso)
                        .thenComparing(TempoTramitacaoInicialProcessoResponse::processoId))
                .toList();
        double tempoInicial = detalhesTempoInicial.stream()
                .mapToLong(TempoTramitacaoInicialProcessoResponse::diasCorridos)
                .average().orElse(0);
        Map<String, Long> formalizacoes = new LinkedHashMap<>();
        portfolio.forEach(i -> formalizacoes.merge(YearMonth.from(i.getDataFormalizacao()).toString(), 1L, Long::sum));
        Map<String, Long> conclusoes = new LinkedHashMap<>();
        portfolio.stream().filter(i -> i.getVigenciaContratualFinal().isBefore(hoje))
                .forEach(i -> conclusoes.merge(YearMonth.from(i.getVigenciaContratualFinal()).toString(), 1L, Long::sum));
        return new DashboardResponse(porStatus, percentual, alertasContrato, alertasTed, valor, porTipo,
                permanencia.medias(), permanencia.gargalo(), permanencia.detalhes(),
                tempoInicial, detalhesTempoInicial, formalizacoes, conclusoes);
    }

    private Permanencia calcularPermanencias(List<ProcessoAdministrativo> ativos, LocalDate hoje) {
        Map<String, List<PermanenciaProcessoDashboardResponse>> detalhes = new TreeMap<>();
        Map<Long, ProcessoAdministrativo> processosPorId = ativos.stream().collect(
                java.util.stream.Collectors.toMap(ProcessoAdministrativo::getId, p -> p));
        for (var historico : leituraTramitacao.historicos(
                processosPorId.keySet(), Set.of(ContextoTramitacao.FORMALIZACAO), hoje)) {
            ProcessoAdministrativo p = processosPorId.get(historico.processoId());
            historico.permanencias().forEach(permanencia ->
                    detalhes.computeIfAbsent(
                                    permanencia.setor().sigla(), key -> new ArrayList<>())
                            .add(new PermanenciaProcessoDashboardResponse(
                                    p.getId(), p.getNumero(), permanencia.dataChegada(),
                                    permanencia.dataSaida(), permanencia.diasCorridos(),
                                    permanencia.aberta())));
        }
        Map<String, Double> medias = new LinkedHashMap<>();
        detalhes.forEach((setor, permanencias) -> medias.put(setor, permanencias.stream()
                .mapToLong(PermanenciaProcessoDashboardResponse::diasCorridos)
                .average().orElse(0)));
        String gargalo = medias.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        Map<String, List<PermanenciaProcessoDashboardResponse>> detalhesOrdenados =
                new LinkedHashMap<>();
        detalhes.forEach((setor, permanencias) -> detalhesOrdenados.put(setor, permanencias.stream()
                .sorted(Comparator.comparing(PermanenciaProcessoDashboardResponse::numeroProcesso)
                        .thenComparing(PermanenciaProcessoDashboardResponse::dataChegada)
                        .thenComparing(PermanenciaProcessoDashboardResponse::processoId))
                .toList()));
        return new Permanencia(medias, gargalo, detalhesOrdenados);
    }

    private record Permanencia(
            Map<String, Double> medias,
            String gargalo,
            Map<String, List<PermanenciaProcessoDashboardResponse>> detalhes) {}
    private record ItemPortfolio(
            ProcessoAdministrativo processo,
            InstrumentoContratual instrumento,
            StatusProcesso status) {}
}
