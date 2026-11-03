package com.moments.sicc.service;

import static com.moments.sicc.service.ConteudoRelatorio.Celula.data;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.dataHora;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.enumeracao;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.logico;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.numero;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.moeda;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.percentual;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.texto;
import static com.moments.sicc.service.ConteudoRelatorio.Celula.vazia;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.domain.Enums.TipoRelatorio;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.service.LeituraTramitacao.PermanenciaLida;
import com.moments.sicc.service.RegrasDeVigencia.ReferenciaDeVigencia;
import com.moments.sicc.api.ApiDtos.DashboardResponse;
import com.moments.sicc.shared.exception.DomainException;
import java.time.YearMonth;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
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
            "numero", "origem", "tipo", "status", "vigenciaContratual", "vigenciaTed", "setorId", "responsavelId");

    private final InstrumentoContratualRepository instrumentos;
    private final LeituraTramitacao leituraTramitacao;
    private final IndicadoresPortfolio indicadores;
    private final Map<TipoRelatorio, Definicao> definicoes;

    public GeracaoRelatorios(
            InstrumentoContratualRepository instrumentos,
            LeituraTramitacao leituraTramitacao, IndicadoresPortfolio indicadores) {
        this.instrumentos = instrumentos;
        this.leituraTramitacao = leituraTramitacao;
        this.indicadores = indicadores;
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
        DadosGeracao dadosGeracao = carregar(contexto);
        List<ConteudoRelatorio.Linha> dados = definicao.gerador().apply(dadosGeracao);
        if (contexto.processos().isEmpty()
                || dados.stream().noneMatch(linha -> linha.papel() == ConteudoRelatorio.Papel.DADOS)) {
            dados = new ArrayList<>(dados);
            dados.add(secao("Nenhum registro encontrado para os filtros selecionados."));
        }
        return ConteudoRelatorio.construtor()
                .titulo(texto("relatorio"), texto(definicao.titulo()))
                .metadado(texto("filtros"), texto(formatarFiltros(dadosGeracao)))
                .metadado(texto("autor"), texto(emissao.autorNome()
                        + " (" + emissao.autorLogin() + ")"))
                .metadado(texto("gerado_em"), dataHora(emissao.geradoEm()))
                .linha()
                .adicionar(dados)
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

    private List<ConteudoRelatorio.Linha> dadosConsolidados(DadosGeracao contexto) {
        List<ConteudoRelatorio.Linha> linhas = new ArrayList<>();
        linhas.add(textos("numero_processo", "origem", "status", "tipo_instrumento",
                "coordenador", "vigencia_contratual", "vigencia_ted", "valor_atual",
                "numero_instrumento", "objeto", "descricao", "natureza", "participes",
                "data_cadastro", "numero_projeto", "responsavel_dipac", "data_formalizacao",
                "setor_atual", "tempo_tramitacao_inicial_dias"));
        var setores = contexto.setores();
        for (ProcessoAdministrativo processo : processosOrdenados(contexto)) {
            InstrumentoContratual instrumento = contexto.instrumentos().get(processo.getId());
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
                        numero(instrumento.getValorAtual()),
                        texto(instrumento.getNumero()), texto(instrumento.getObjeto()),
                        texto(instrumento.getDescricao()), texto(instrumento.getNatureza()),
                        texto(instrumento.getParticipes()), data(processo.getDataCadastro()),
                        texto(processo.getNumeroProjeto()), texto(nomeResponsavel(processo)),
                        data(instrumento.getDataFormalizacao()),
                        texto(setores.containsKey(processo.getId()) ? setores.get(processo.getId()).sigla() : null),
                        numero(tempoInicial(processo, instrumento.getDataFormalizacao(), contexto.hoje()))));
            } else {
                linhas.add(linha(
                        texto(processo.getNumero()), texto(processo.getOrigem()),
                        enumeracao(contexto.referencia().status(null)),
                        vazia(), vazia(), vazia(), vazia(), vazia(),
                        vazia(), vazia(), vazia(), vazia(), vazia(),
                        data(processo.getDataCadastro()), texto(processo.getNumeroProjeto()),
                        texto(nomeResponsavel(processo)), vazia(),
                        texto(setores.containsKey(processo.getId()) ? setores.get(processo.getId()).sigla() : null),
                        numero(tempoInicial(processo, null, contexto.hoje()))));
            }
        }
        return linhas;
    }

    private List<ConteudoRelatorio.Linha> consolidado(DadosGeracao contexto) {
        DashboardResponse resumo = resumir(contexto);
        List<ConteudoRelatorio.Linha> linhas = new ArrayList<>();
        adicionarIndicadores(linhas, resumo);
        linhas.add(nota("Indicadores e valores vigentes na data de emissão, considerando somente os processos ativos que correspondem aos filtros."));
        adicionarDistribuicoes(linhas, resumo);
        adicionarSerie(linhas, contexto, resumo, null);
        linhas.add(secao("Processos Administrativos da carteira filtrada"));
        linhas.addAll(dadosConsolidados(contexto));
        return linhas;
    }

    private List<ConteudoRelatorio.Linha> relatorioAnual(DadosGeracao contexto) {
        int ano = filtro(contexto.filtros(), "ano") == null ? contexto.hoje().getYear()
                : Integer.parseInt(filtro(contexto.filtros(), "ano"));
        if (ano > contexto.hoje().getYear()) throw new DomainException("O ano do relatório não pode ser futuro.");
        DashboardResponse resumo = resumir(contexto);
        List<ConteudoRelatorio.Linha> linhas = new ArrayList<>();
        linhas.add(textos("ano", "total", "em_formalizacao", "em_vigencia", "concluido"));
        var cadastrados = contexto.processos().stream().filter(p -> p.getDataCadastro().getYear() == ano).toList();
        EnumMap<StatusProcesso, Long> contagem = new EnumMap<>(StatusProcesso.class);
        cadastrados.forEach(p -> contagem.merge(status(p, contexto), 1L, Long::sum));
        linhas.add(linha(numero(ano), numero(cadastrados.size()),
                numero(contagem.getOrDefault(StatusProcesso.EM_FORMALIZACAO, 0L)),
                numero(contagem.getOrDefault(StatusProcesso.EM_VIGENCIA, 0L)),
                numero(contagem.getOrDefault(StatusProcesso.CONCLUIDO, 0L))));
        linhas.add(nota("A primeira tabela considera os cadastros do ano e seu status atual. A atividade abaixo inclui também processos cadastrados em anos anteriores. Não representa a carteira histórica em 31 de dezembro."));
        linhas.add(secao("Atividade registrada em " + ano));
        linhas.add(textos("indicador", "resultado", "unidade"));
        linhas.add(metrica("cadastros_ano", numero(cadastrados.size()), "processos"));
        linhas.add(metrica("formalizacoes_ano", numero(totalAno(resumo.formalizacoesMensais(), ano)), "instrumentos"));
        linhas.add(metrica("conclusoes_ano", numero(totalAno(resumo.conclusoesMensais(), ano)), "processos"));
        var formalizados = resumo.detalhesTempoTramitacaoInicial().stream()
                .filter(d -> d.dataFormalizacao() != null && d.dataFormalizacao().getYear() == ano
                        && !d.dataFormalizacao().isAfter(contexto.hoje())).toList();
        linhas.add(metrica("tempo_medio_formalizacoes_ano", decimal(formalizados.stream()
                .mapToLong(d -> d.diasCorridos()).average().orElse(0)), "dias"));
        linhas.add(nota("Tempo médio das formalizações: do cadastro até a formalização, para os instrumentos formalizados no ano. Conclusões usam a vigência contratual atual vencida, como no dashboard; prorrogações podem alterar a contagem."));
        adicionarSerie(linhas, contexto, resumo, ano);
        var periodos = permanenciasDoAno(resumo, ano, contexto.hoje());
        linhas.add(secao("Permanência nos setores durante " + ano));
        linhas.add(textos("setor", "periodos", "permanencia_media_dias"));
        periodos.forEach((setor, dias) -> linhas.add(linha(texto(setor), numero(dias.size()),
                decimal(dias.stream().mapToLong(Long::longValue).average().orElse(0)))));
        if (periodos.isEmpty()) linhas.add(nota("Nenhuma permanência registrada no período."));
        else {
            String gargalo = periodos.entrySet().stream().sorted(Comparator
                    .<Map.Entry<String, List<Long>>>comparingDouble(e -> e.getValue().stream()
                            .mapToLong(Long::longValue).average().orElse(0)).reversed()
                    .thenComparing(Map.Entry::getKey)).findFirst().orElseThrow().getKey();
            linhas.add(nota("Maior gargalo do ano: " + gargalo + ". Os intervalos de permanência são limitados ao ano e à data de emissão; correções no mesmo setor não reiniciam o período."));
        }
        linhas.add(secao("Carteira atual filtrada · todos os anos de cadastro"));
        adicionarIndicadores(linhas, resumo);
        adicionarDistribuicoes(linhas, resumo);
        linhas.add(nota("Esta seção usa a mesma referência e as mesmas fórmulas do dashboard. Os status, os valores e as médias da carteira são atuais, e não uma reconstrução histórica do ano selecionado."));
        var participantes = contexto.processos().stream().filter(p -> participouDoAno(p, contexto, resumo, ano)).toList();
        linhas.add(secao("Processos com cadastro ou atividade no ano"));
        linhas.addAll(dadosConsolidados(contexto.selecionar(participantes)));
        if (participantes.isEmpty()) linhas.add(nota("Nenhum cadastro ou atividade encontrado no ano selecionado."));
        return linhas;
    }

    private DashboardResponse resumir(DadosGeracao contexto) {
        return indicadores.calcular(contexto.processos(), contexto.instrumentos(), contexto.hoje(), contexto.referencia());
    }

    private void adicionarIndicadores(List<ConteudoRelatorio.Linha> linhas, DashboardResponse resumo) {
        linhas.add(textos("indicador", "resultado", "unidade"));
        linhas.add(metrica("total_processos", numero(resumo.processosPorStatus().values().stream().mapToLong(Long::longValue).sum()), "processos"));
        for (StatusProcesso status : StatusProcesso.values()) {
            linhas.add(metrica(status.name().toLowerCase(java.util.Locale.ROOT), numero(resumo.processosPorStatus().getOrDefault(status, 0L)), "processos"));
        }
        linhas.add(metrica("percentual_concluidos", percentual(resumo.percentualConcluidos()), "%"));
        linhas.add(metrica("valor_total_vigente", moeda(resumo.valorTotalVigente()), "R$"));
        linhas.add(metrica("alertas_contratuais", numero(resumo.alertasContratuais()), "instrumentos"));
        linhas.add(metrica("alertas_ted", numero(resumo.alertasTed()), "instrumentos"));
        linhas.add(metrica("tempo_medio_tramitacao_inicial", decimal(resumo.tempoMedioTramitacaoInicialDias()), "dias"));
        linhas.add(metrica("maior_gargalo", texto(resumo.maiorGargalo() == null ? "Não identificado" : resumo.maiorGargalo()), "setor"));
    }

    private void adicionarDistribuicoes(List<ConteudoRelatorio.Linha> linhas, DashboardResponse resumo) {
        linhas.add(secao("Instrumentos por tipo na carteira atual"));
        linhas.add(textos("tipo_instrumento", "quantidade"));
        for (TipoInstrumento tipo : TipoInstrumento.values()) {
            linhas.add(linha(enumeracao(tipo), numero(resumo.instrumentosPorTipo().getOrDefault(tipo, 0L))));
        }
        linhas.add(secao("Permanência média por setor na carteira atual"));
        linhas.add(textos("setor", "permanencia_media_dias"));
        new TreeMap<>(resumo.permanenciaMediaPorSetor()).forEach((setor, media) -> linhas.add(linha(texto(setor), decimal(media))));
        if (resumo.permanenciaMediaPorSetor().isEmpty()) linhas.add(nota("Nenhuma permanência registrada para a carteira filtrada."));
    }

    private void adicionarSerie(List<ConteudoRelatorio.Linha> linhas, DadosGeracao contexto, DashboardResponse resumo, Integer ano) {
        linhas.add(secao(ano == null ? "Atividade mensal registrada" : "Atividade mensal de " + ano));
        linhas.add(textos("mes", "cadastros", "formalizacoes", "conclusoes"));
        Map<String, Long> cadastros = contexto.processos().stream().collect(Collectors.groupingBy(
                p -> YearMonth.from(p.getDataCadastro()).toString(), TreeMap::new, Collectors.counting()));
        Set<String> meses = new java.util.TreeSet<>();
        if (ano != null) {
            int ultimo = ano == contexto.hoje().getYear() ? contexto.hoje().getMonthValue() : 12;
            for (int mes = 1; mes <= ultimo; mes++) meses.add(YearMonth.of(ano, mes).toString());
        } else {
            meses.addAll(cadastros.keySet()); meses.addAll(resumo.formalizacoesMensais().keySet()); meses.addAll(resumo.conclusoesMensais().keySet());
        }
        meses.forEach(mes -> linhas.add(linha(texto(mes), numero(cadastros.getOrDefault(mes, 0L)),
                numero(resumo.formalizacoesMensais().getOrDefault(mes, 0L)), numero(resumo.conclusoesMensais().getOrDefault(mes, 0L)))));
    }

    private Map<String, List<Long>> permanenciasDoAno(DashboardResponse resumo, int ano, LocalDate hoje) {
        Map<String, List<Long>> resultado = new TreeMap<>();
        LocalDate inicio = LocalDate.of(ano, 1, 1);
        LocalDate fim = ano == hoje.getYear() ? hoje : LocalDate.of(ano + 1, 1, 1);
        resumo.detalhesPermanenciaPorSetor().forEach((setor, periodos) -> periodos.forEach(p -> {
            LocalDate saida = p.dataSaida() == null ? hoje : p.dataSaida();
            if (p.dataChegada().getYear() > ano || p.dataChegada().isAfter(fim) || saida.isBefore(inicio)
                    || p.dataSaida() != null && !saida.isAfter(inicio) && p.dataChegada().isBefore(inicio)) return;
            LocalDate a = p.dataChegada().isBefore(inicio) ? inicio : p.dataChegada();
            LocalDate b = saida.isAfter(fim) ? fim : saida;
            if (!a.isAfter(b)) resultado.computeIfAbsent(setor, k -> new ArrayList<>()).add(ChronoUnit.DAYS.between(a, b));
        }));
        return resultado;
    }

    private boolean participouDoAno(ProcessoAdministrativo p, DadosGeracao contexto, DashboardResponse resumo, int ano) {
        if (p.getDataCadastro().getYear() == ano) return true;
        InstrumentoContratual i = contexto.instrumentos().get(p.getId());
        if (i != null && (i.getDataFormalizacao() != null && i.getDataFormalizacao().getYear() == ano
                || i.getVigenciaContratualFinal().getYear() == ano && i.getVigenciaContratualFinal().isBefore(contexto.hoje()))) return true;
        return resumo.detalhesPermanenciaPorSetor().values().stream().flatMap(List::stream)
                .anyMatch(d -> d.processoId().equals(p.getId()) && d.dataChegada().getYear() <= ano
                        && (d.dataSaida() == null ? contexto.hoje() : d.dataSaida()).getYear() >= ano);
    }

    private long totalAno(Map<String, Long> meses, int ano) {
        return meses.entrySet().stream().filter(e -> YearMonth.parse(e.getKey()).getYear() == ano)
                .mapToLong(Map.Entry::getValue).sum();
    }

    private long tempoInicial(ProcessoAdministrativo processo, LocalDate formalizacao, LocalDate hoje) {
        return Math.max(0, ChronoUnit.DAYS.between(processo.getDataCadastro(), formalizacao == null ? hoje : formalizacao));
    }

    private ConteudoRelatorio.Celula decimal(double valor) {
        return numero(BigDecimal.valueOf(valor).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private ConteudoRelatorio.Linha metrica(String nome, ConteudoRelatorio.Celula valor, String unidade) {
        return linha(texto(nome), valor, texto(unidade));
    }

    private ConteudoRelatorio.Linha nota(String texto) {
        return new ConteudoRelatorio.Linha(List.of(texto(texto)), ConteudoRelatorio.Papel.NOTA);
    }

    private StatusProcesso status(ProcessoAdministrativo processo, DadosGeracao contexto) {
        return contexto.referencia().status(java.util.Optional.ofNullable(contexto.instrumentos().get(processo.getId()))
                .map(InstrumentoContratual::getVigenciaContratualFinal).orElse(null));
    }

    private List<ConteudoRelatorio.Linha> instrumentosPorTipo(DadosGeracao contexto) {
        List<ConteudoRelatorio.Linha> linhas = new ArrayList<>();
        linhas.add(textos("tipo_instrumento", "quantidade", "valor_total_atual"));
        Map<TipoInstrumento, List<InstrumentoContratual>> grupos = new EnumMap<>(TipoInstrumento.class);
        contexto.processos().stream()
                .map(processo -> contexto.instrumentos().get(processo.getId()))
                .filter(Objects::nonNull)
                .forEach(instrumento -> grupos.computeIfAbsent(
                        instrumento.getTipo(), ignored -> new ArrayList<>()).add(instrumento));
        grupos.forEach((tipo, lista) -> linhas.add(linha(
                enumeracao(tipo),
                numero(lista.size()),
                numero(lista.stream().map(InstrumentoContratual::getValorAtual)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)))));
        List<ProcessoAdministrativo> selecionados = processosOrdenados(contexto).stream()
                .filter(processo -> contexto.instrumentos().containsKey(processo.getId())).toList();
        if (!selecionados.isEmpty()) {
            linhas.add(secao("Instrumentos Contratuais por processo"));
            linhas.addAll(dadosConsolidados(contexto.selecionar(selecionados)));
        }
        return linhas;
    }

    private List<ConteudoRelatorio.Linha> historicoTramitacoes(DadosGeracao contexto) {
        List<ConteudoRelatorio.Linha> linhas = new ArrayList<>();
        linhas.add(textos(
                "numero_processo", "contexto", "contexto_id", "data", "sequencia",
                "setor_destino", "autor", "observacao", "permanencia_inicio",
                "permanencia_fim", "permanencia_dias", "permanencia_aberta"));
        Map<Long, ProcessoAdministrativo> processosPermitidos = contexto.processos().stream()
                .collect(Collectors.toMap(ProcessoAdministrativo::getId, processo -> processo));
        Set<ContextoTramitacao> tipos = EnumSet.allOf(ContextoTramitacao.class);
        String tipoFiltrado = filtro(contexto.filtros(), "contexto");
        tipos.removeIf(tipo -> tipoFiltrado != null && !tipo.name().equals(tipoFiltrado));
        for (var historico : leituraTramitacao.historicos(
                processosPermitidos.keySet(), tipos, contexto.hoje())) {
            ProcessoAdministrativo processo = processosPermitidos.get(historico.processoId());
            for (var atual : historico.movimentacoes()) {
                PermanenciaLida permanencia = atual.permanenciaIniciada();
                boolean periodoAbertoNoIntervalo = permanencia != null && permanencia.aberta()
                        && permanenciaIntersecaIntervalo(permanencia, contexto.filtros(), contexto.hoje());
                if (!dataNoIntervalo(atual.dataMovimentacao(), contexto.filtros())
                        && !periodoAbertoNoIntervalo) continue;
                linhas.add(linha(
                        texto(processo.getNumero()),
                        enumeracao(historico.contexto().tipo()),
                        numero(historico.contexto().id()),
                        data(atual.dataMovimentacao()),
                        numero(atual.sequenciaDiaria()),
                        texto(atual.setorDestino().sigla()),
                        texto(atual.autor().nome()),
                        texto(atual.observacao()),
                        data(permanencia == null ? null : permanencia.dataChegada()),
                        data(permanencia == null ? null : permanencia.dataSaida()),
                        numero(permanencia == null ? null : permanencia.diasCorridos()),
                        logico(permanencia == null ? null : permanencia.aberta())));
            }
        }
        return linhas;
    }

    private boolean permanenciaIntersecaIntervalo(
            PermanenciaLida permanencia, Map<String, String> filtros, LocalDate hoje) {
        LocalDate inicio = filtro(filtros, "dataInicial") == null
                ? null : LocalDate.parse(filtro(filtros, "dataInicial"));
        LocalDate fim = filtro(filtros, "dataFinal") == null
                ? null : LocalDate.parse(filtro(filtros, "dataFinal"));
        LocalDate fimPermanencia = permanencia.dataSaida() == null ? hoje : permanencia.dataSaida();
        return (fim == null || !permanencia.dataChegada().isAfter(fim))
                && (inicio == null || !fimPermanencia.isBefore(inicio));
    }

    private List<ConteudoRelatorio.Linha> vigencias(DadosGeracao contexto) {
        List<ConteudoRelatorio.Linha> linhas = new ArrayList<>();
        linhas.add(textos(
                "numero_processo", "tipo_instrumento", "vigencia_contratual",
                "situacao_contratual", "dias_ate_vencimento_contratual",
                "no_horizonte_120_contratual", "vigencia_ted", "situacao_ted",
                "dias_ate_vencimento_ted", "no_horizonte_120_ted",
                "numero_instrumento", "origem", "coordenador"));
        processosOrdenados(contexto).forEach(processo -> java.util.Optional.ofNullable(contexto.instrumentos().get(processo.getId()))
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
                                instrumento.getVigenciaTedFinal(), contexto.referencia())),
                        texto(instrumento.getNumero()), texto(processo.getOrigem()),
                        texto(instrumento.getCoordenador())))));
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

    private String formatarFiltros(DadosGeracao contexto) {
        Map<String, String> filtros = contexto.filtros();
        if (filtros == null || filtros.isEmpty()) return "Sem filtros";
        return new TreeMap<>(filtros).entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> entry.getKey() + "=" + entry.getValue().trim() + descreverVinculo(entry.getKey(), entry.getValue(), contexto))
                .collect(Collectors.joining(" | "));
    }

    private ConteudoRelatorio.Linha linha(ConteudoRelatorio.Celula... celulas) {
        return new ConteudoRelatorio.Linha(java.util.Arrays.asList(celulas));
    }

    private ConteudoRelatorio.Linha textos(String... valores) {
        return new ConteudoRelatorio.Linha(java.util.Arrays.stream(valores)
                .map(ConteudoRelatorio.Celula::texto).toList(), ConteudoRelatorio.Papel.CABECALHO);
    }

    private ConteudoRelatorio.Linha secao(String titulo) {
        return new ConteudoRelatorio.Linha(List.of(texto(titulo)), ConteudoRelatorio.Papel.SECAO);
    }

    private List<ProcessoAdministrativo> processosOrdenados(DadosGeracao contexto) {
        return contexto.processos().stream().sorted(
                Comparator.comparing(ProcessoAdministrativo::getNumero)).toList();
    }

    private String nomeResponsavel(ProcessoAdministrativo processo) {
        return processo.getResponsavel() == null ? null : processo.getResponsavel().getNome();
    }

    private String descreverVinculo(String chave, String valor, DadosGeracao contexto) {
        if (chave.equals("setorId")) {
            if (valor.equals("SEM_SETOR")) return " (Sem setor atual)";
            return contexto.setores().values().stream().filter(s -> s.id().toString().equals(valor))
                    .map(s -> " (" + s.sigla() + " - " + s.nome() + ")").findFirst().orElse("");
        }
        if (chave.equals("responsavelId")) {
            if (valor.equals("SEM_RESPONSAVEL")) return " (Sem responsável)";
            return contexto.processos().stream().map(ProcessoAdministrativo::getResponsavel)
                    .filter(Objects::nonNull).filter(u -> u.getId().toString().equals(valor))
                    .map(u -> " (" + u.getNome() + ")").findFirst().orElse("");
        }
        return "";
    }

    private DadosGeracao carregar(Contexto contexto) {
        Map<Long, InstrumentoContratual> carregados = new java.util.HashMap<>();
        contexto.processos().forEach(p -> instrumentos.findByProcessoId(p.getId()).ifPresent(i -> carregados.put(p.getId(), i)));
        var setores = leituraTramitacao.setoresAtuais(contexto.processos().stream().map(ProcessoAdministrativo::getId).toList());
        return new DadosGeracao(contexto, Map.copyOf(carregados), Map.copyOf(setores));
    }

    private record DadosGeracao(Contexto contexto, Map<Long, InstrumentoContratual> instrumentos,
                                Map<Long, LeituraTramitacao.SetorLido> setores) {
        Map<String, String> filtros() { return contexto.filtros(); }
        List<ProcessoAdministrativo> processos() { return contexto.processos(); }
        EmissaoRelatorio emissao() { return contexto.emissao(); }
        LocalDate hoje() { return contexto.hoje(); }
        ReferenciaDeVigencia referencia() { return contexto.referencia(); }
        DadosGeracao selecionar(List<ProcessoAdministrativo> processos) {
            return new DadosGeracao(new Contexto(filtros(), processos, emissao()), instrumentos, setores);
        }
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

    private record Definicao(
            String titulo,
            Set<String> filtrosEspecificos,
            Function<DadosGeracao, List<ConteudoRelatorio.Linha>> gerador) {}
}
