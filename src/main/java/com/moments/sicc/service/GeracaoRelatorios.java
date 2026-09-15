package com.moments.sicc.service;

import static com.moments.sicc.service.ConteudoRelatorio.Celula.data;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.dataHora;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.enumeracao;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.logico;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.numero;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.texto;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.vazia;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.domain.Enums.TipoRelatorio;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.MovimentacaoRepository;
import com.moments.sicc.service.CalculadoraPermanencia.Periodo;
import com.moments.sicc.service.RegrasDeVigencia.ReferenciaDeVigencia;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class GeracaoRelatorios {
    private static final Set<String> FILTROS_COMUNS = Set.of(
            "numero", "origem", "tipo", "status", "vigenciaContratual", "vigenciaTed");

    private final InstrumentoContratualRepository instrumentos;
    private final AlteracaoContratualRepository alteracoes;
    private final MovimentacaoRepository movimentacoes;
    private final CalculadoraPermanencia calculadoraPermanencia;
    private final Map<TipoRelatorio, Definicao> definicoes;

    public GeracaoRelatorios(
            InstrumentoContratualRepository instrumentos,
            AlteracaoContratualRepository alteracoes,
            MovimentacaoRepository movimentacoes,
            CalculadoraPermanencia calculadoraPermanencia) {
        this.instrumentos = instrumentos;
        this.alteracoes = alteracoes;
        this.movimentacoes = movimentacoes;
        this.calculadoraPermanencia = calculadoraPermanencia;
        EnumMap<TipoRelatorio, Definicao> catalogo =
                new EnumMap<>(TipoRelatorio.class);
        catalogo.put(TipoRelatorio.ANUAL_PROCESSOS, new Definicao(
                "Relatório anual de processos", Set.of("ano"), this::relatorioAnual));
        catalogo.put(TipoRelatorio.INSTRUMENTOS_POR_TIPO, new Definicao(
                "Relatório de instrumentos por tipo", Set.of(), this::instrumentosPorTipo));
        catalogo.put(TipoRelatorio.HISTORICO_TRAMITACOES, new Definicao(
                "Relatório do histórico de tramitações",
                Set.of("contexto", "dataInicial", "dataFinal"),
                this::historicoTramitacoes));
        catalogo.put(TipoRelatorio.VIGENCIAS, new Definicao(
                "Relatório de vigências", Set.of(), this::vigencias));
        catalogo.put(TipoRelatorio.CONSOLIDADO, new Definicao(
                "Relatório consolidado", Set.of(), this::consolidado));
        if (catalogo.size() != TipoRelatorio.values().length) {
            throw new IllegalStateException("O catálogo não cobre todos os tipos de relatório.");
        }
        this.definicoes = Collections.unmodifiableMap(catalogo);
    }

    ConteudoRelatorio gerar(TipoRelatorio tipo, Contexto contexto) {
        Definicao definicao = definicao(tipo);
        contexto = Objects.requireNonNull(contexto);
        EmissaoRelatorio emissao = contexto.emissao();
        return ConteudoRelatorio.construtor()
                .linha(texto("relatorio"), texto(definicao.titulo()))
                .linha(texto("filtros"), texto(formatarFiltros(contexto.filtros())))
                .linha(texto("autor"), texto(emissao.autorNome()
                        + " (" + emissao.autorLogin() + ")"))
                .linha(texto("gerado_em"), dataHora(emissao.geradoEm()))
                .linha()
                .adicionar(definicao.gerador().apply(contexto))
                .construir();
    }

    public String titulo(TipoRelatorio tipo) {
        return definicao(tipo).titulo();
    }

    public Set<String> filtrosPermitidos(TipoRelatorio tipo) {
        Set<String> permitidos = new HashSet<>(FILTROS_COMUNS);
        permitidos.addAll(definicao(tipo).filtrosEspecificos());
        return Collections.unmodifiableSet(permitidos);
    }

    private Definicao definicao(TipoRelatorio tipo) {
        return Objects.requireNonNull(
                definicoes.get(Objects.requireNonNull(tipo)),
                "Tipo de relatório sem definição.");
    }

    private List<ConteudoRelatorio.Linha> consolidado(Contexto contexto) {
        List<ConteudoRelatorio.Linha> linhas = new ArrayList<>();
        linhas.add(textos("numero_processo", "origem", "status", "tipo_instrumento",
                "coordenador", "vigencia_contratual", "vigencia_ted", "valor_atual"));
        for (ProcessoAdministrativo processo : contexto.processos()) {
            InstrumentoContratual instrumento = instrumentos.findByProcessoId(processo.getId()).orElse(null);
            if (instrumento != null) {
                linhas.add(linha(
                        texto(processo.getNumero()),
                        texto(processo.getOrigem()),
                        enumeracao(contexto.referencia().status(
                                instrumento.getVigenciaContratualFinal())),
                        enumeracao(instrumento.getTipo()),
                        texto(instrumento.getCoordenador()),
                        data(instrumento.getVigenciaContratualFinal()),
                        data(instrumento.getVigenciaTedFinal()),
                        numero(instrumento.getValorAtual())));
            } else {
                linhas.add(linha(
                        texto(processo.getNumero()), texto(processo.getOrigem()),
                        enumeracao(contexto.referencia().status(null)),
                        vazia(), vazia(), vazia(), vazia(), vazia()));
            }
        }
        return linhas;
    }

    private List<ConteudoRelatorio.Linha> relatorioAnual(Contexto contexto) {
        List<ConteudoRelatorio.Linha> linhas = new ArrayList<>();
        linhas.add(textos("ano", "total", "em_formalizacao", "em_vigencia", "concluido"));
        Map<Integer, EnumMap<StatusProcesso, Long>> porAno = new java.util.TreeMap<>();
        contexto.processos().stream()
                .filter(processo -> filtro(contexto.filtros(), "ano") == null
                        || Integer.toString(processo.getDataCadastro().getYear())
                                .equals(filtro(contexto.filtros(), "ano")))
                .forEach(processo -> {
                    EnumMap<StatusProcesso, Long> contagem = porAno.computeIfAbsent(
                            processo.getDataCadastro().getYear(), ignored -> new EnumMap<>(StatusProcesso.class));
                    InstrumentoContratual instrumento = instrumentos.findByProcessoId(processo.getId()).orElse(null);
                    contagem.merge(contexto.referencia().status(
                            instrumento == null ? null : instrumento.getVigenciaContratualFinal()), 1L, Long::sum);
                });
        porAno.forEach((ano, contagem) -> linhas.add(linha(
                numero(ano),
                numero(contagem.values().stream().mapToLong(Long::longValue).sum()),
                numero(contagem.getOrDefault(StatusProcesso.EM_FORMALIZACAO, 0L)),
                numero(contagem.getOrDefault(StatusProcesso.EM_VIGENCIA, 0L)),
                numero(contagem.getOrDefault(StatusProcesso.CONCLUIDO, 0L)))));
        return linhas;
    }

    private List<ConteudoRelatorio.Linha> instrumentosPorTipo(Contexto contexto) {
        List<ConteudoRelatorio.Linha> linhas = new ArrayList<>();
        linhas.add(textos("tipo_instrumento", "quantidade", "valor_total_atual"));
        Map<TipoInstrumento, List<InstrumentoContratual>> grupos = new EnumMap<>(TipoInstrumento.class);
        contexto.processos().stream()
                .map(processo -> instrumentos.findByProcessoId(processo.getId()).orElse(null))
                .filter(Objects::nonNull)
                .forEach(instrumento -> grupos.computeIfAbsent(
                        instrumento.getTipo(), ignored -> new ArrayList<>()).add(instrumento));
        grupos.forEach((tipo, lista) -> linhas.add(linha(
                enumeracao(tipo),
                numero(lista.size()),
                numero(lista.stream().map(InstrumentoContratual::getValorAtual)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)))));
        return linhas;
    }

    private List<ConteudoRelatorio.Linha> historicoTramitacoes(Contexto contexto) {
        List<ConteudoRelatorio.Linha> linhas = new ArrayList<>();
        linhas.add(textos(
                "numero_processo", "contexto", "contexto_id", "data", "sequencia",
                "setor_destino", "autor", "observacao", "permanencia_inicio",
                "permanencia_fim", "permanencia_dias", "permanencia_aberta"));
        Map<Long, ProcessoAdministrativo> processosPermitidos = contexto.processos().stream()
                .collect(Collectors.toMap(ProcessoAdministrativo::getId, processo -> processo));
        List<Movimentacao> percursoCompleto = movimentacoes.findAll().stream()
                .filter(movimento -> processoDaMovimentacao(movimento, processosPermitidos) != null)
                .sorted(java.util.Comparator.comparing(Movimentacao::getContextoTipo)
                        .thenComparing(Movimentacao::getContextoId)
                        .thenComparing(Movimentacao::getDataMovimentacao)
                        .thenComparing(Movimentacao::getSequenciaDiaria))
                .toList();
        Map<Long, Periodo> permanenciasPorMovimento = new java.util.HashMap<>();
        percursoCompleto.stream().collect(Collectors.groupingBy(
                        movimento -> new ContextoMovimentacao(
                                movimento.getContextoTipo(), movimento.getContextoId()),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .values().forEach(percurso -> calculadoraPermanencia.calcular(
                        percurso, contexto.hoje()).forEach(permanencia ->
                            permanenciasPorMovimento.put(permanencia.movimentacaoChegadaId(), permanencia)));
        for (Movimentacao atual : percursoCompleto) {
            if (filtro(contexto.filtros(), "contexto") != null
                    && !atual.getContextoTipo().name().equals(filtro(contexto.filtros(), "contexto"))) continue;
            Periodo permanencia = permanenciasPorMovimento.get(atual.getId());
            boolean periodoAbertoNoIntervalo = permanencia != null && permanencia.aberta()
                    && permanenciaIntersecaIntervalo(permanencia, contexto.filtros(), contexto.hoje());
            if (!dataNoIntervalo(atual.getDataMovimentacao(), contexto.filtros())
                    && !periodoAbertoNoIntervalo) continue;
            ProcessoAdministrativo processo = processoDaMovimentacao(atual, processosPermitidos);
            linhas.add(linha(
                    texto(processo.getNumero()),
                    enumeracao(atual.getContextoTipo()),
                    numero(atual.getContextoId()),
                    data(atual.getDataMovimentacao()),
                    numero(atual.getSequenciaDiaria()),
                    texto(atual.getSetorDestino().getSigla()),
                    texto(atual.getAutor().getNome()),
                    texto(atual.getObservacao()),
                    data(permanencia == null ? null : permanencia.dataChegada()),
                    data(permanencia == null ? null : permanencia.dataSaida()),
                    numero(permanencia == null ? null : permanencia.diasCorridos()),
                    logico(permanencia == null ? null : permanencia.aberta())));
        }
        return linhas;
    }

    private boolean permanenciaIntersecaIntervalo(
            Periodo permanencia, Map<String, String> filtros, LocalDate hoje) {
        LocalDate inicio = filtro(filtros, "dataInicial") == null
                ? null : LocalDate.parse(filtro(filtros, "dataInicial"));
        LocalDate fim = filtro(filtros, "dataFinal") == null
                ? null : LocalDate.parse(filtro(filtros, "dataFinal"));
        LocalDate fimPermanencia = permanencia.dataSaida() == null ? hoje : permanencia.dataSaida();
        return (fim == null || !permanencia.dataChegada().isAfter(fim))
                && (inicio == null || !fimPermanencia.isBefore(inicio));
    }

    private ProcessoAdministrativo processoDaMovimentacao(
            Movimentacao movimentacao, Map<Long, ProcessoAdministrativo> processosPermitidos) {
        if (movimentacao.getContextoTipo() == ContextoTramitacao.FORMALIZACAO) {
            return processosPermitidos.get(movimentacao.getContextoId());
        }
        return alteracoes.findById(movimentacao.getContextoId())
                .map(alteracao -> processosPermitidos.get(
                        alteracao.getInstrumento().getProcesso().getId()))
                .orElse(null);
    }

    private List<ConteudoRelatorio.Linha> vigencias(Contexto contexto) {
        List<ConteudoRelatorio.Linha> linhas = new ArrayList<>();
        linhas.add(textos(
                "numero_processo", "tipo_instrumento", "vigencia_contratual",
                "situacao_contratual", "dias_ate_vencimento_contratual",
                "no_horizonte_120_contratual", "vigencia_ted", "situacao_ted",
                "dias_ate_vencimento_ted", "no_horizonte_120_ted"));
        contexto.processos().forEach(processo -> instrumentos.findByProcessoId(processo.getId())
                .ifPresent(instrumento -> linhas.add(linha(
                        texto(processo.getNumero()),
                        enumeracao(instrumento.getTipo()),
                        data(instrumento.getVigenciaContratualFinal()),
                        enumeracao(contexto.referencia().situacao(
                                instrumento.getVigenciaContratualFinal())),
                        numero(diasAteVencimento(
                                contexto.hoje(), instrumento.getVigenciaContratualFinal())),
                        logico(noHorizonteDeAlerta(
                                instrumento.getVigenciaContratualFinal(), contexto.referencia())),
                        data(instrumento.getVigenciaTedFinal()),
                        enumeracao(contexto.referencia().situacao(
                                instrumento.getVigenciaTedFinal())),
                        numero(diasAteVencimento(
                                contexto.hoje(), instrumento.getVigenciaTedFinal())),
                        logico(noHorizonteDeAlerta(
                                instrumento.getVigenciaTedFinal(), contexto.referencia()))))));
        return linhas;
    }

    private Long diasAteVencimento(LocalDate hoje, LocalDate vencimento) {
        return vencimento == null ? null : ChronoUnit.DAYS.between(hoje, vencimento);
    }

    private boolean noHorizonteDeAlerta(LocalDate vencimento, ReferenciaDeVigencia referencia) {
        return referencia.situacao(vencimento) == SituacaoVigencia.PROXIMA_VENCIMENTO;
    }

    private boolean dataNoIntervalo(LocalDate data, Map<String, String> filtros) {
        String inicio = filtro(filtros, "dataInicial");
        String fim = filtro(filtros, "dataFinal");
        return (inicio == null || !data.isBefore(LocalDate.parse(inicio)))
                && (fim == null || !data.isAfter(LocalDate.parse(fim)));
    }

    private String filtro(Map<String, String> filtros, String nome) {
        if (filtros == null) return null;
        String valor = filtros.get(nome);
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private String formatarFiltros(Map<String, String> filtros) {
        if (filtros == null || filtros.isEmpty()) return "Sem filtros";
        return new TreeMap<>(filtros).entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> entry.getKey() + "=" + entry.getValue().trim())
                .collect(Collectors.joining(" | "));
    }

    private ConteudoRelatorio.Linha linha(ConteudoRelatorio.Celula... celulas) {
        return new ConteudoRelatorio.Linha(java.util.Arrays.asList(celulas));
    }

    private ConteudoRelatorio.Linha textos(String... valores) {
        return linha(java.util.Arrays.stream(valores)
                .map(ConteudoRelatorio.Celula::texto)
                .toArray(ConteudoRelatorio.Celula[]::new));
    }

    record Contexto(
            Map<String, String> filtros,
            List<ProcessoAdministrativo> processos,
            EmissaoRelatorio emissao) {
        public Contexto {
            filtros = filtros == null ? Map.of() : Map.copyOf(filtros);
            processos = List.copyOf(processos);
            Objects.requireNonNull(emissao);
        }

        LocalDate hoje() {
            return emissao.hoje();
        }

        ReferenciaDeVigencia referencia() {
            return emissao.referencia();
        }
    }

    private record ContextoMovimentacao(ContextoTramitacao tipo, Long id) {}

    private record Definicao(
            String titulo,
            Set<String> filtrosEspecificos,
            Function<Contexto, List<ConteudoRelatorio.Linha>> gerador) {}
}
