package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.FormatoRelatorio;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.domain.Enums.TipoRelatorio;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.RelatorioGerado;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.MovimentacaoRepository;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.repository.RelatorioGeradoRepository;
import com.moments.sicc.shared.ChecksumArquivo;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

@Service
@RequiredArgsConstructor
public class RelatorioDashboardService {
    private final ProcessoAdministrativoRepository processos;
    private final InstrumentoContratualRepository instrumentos;
    private final AlteracaoContratualRepository alteracoes;
    private final MovimentacaoRepository movimentacoes;
    private final RelatorioGeradoRepository relatorios;
    private final ArmazenamentoArquivo storage;
    private final SiccService sicc;
    private final AuditoriaService auditoria;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(
            String origem, TipoInstrumento tipo, StatusProcesso statusSelecionado) {
        LocalDate hoje = LocalDate.now(clock);
        Map<Long, InstrumentoContratual> instrumentoPorProcesso = instrumentos
                .findAllByProcessoAtivoTrue().stream()
                .collect(java.util.stream.Collectors.toMap(
                        instrumento -> instrumento.getProcesso().getId(),
                        instrumento -> instrumento));
        List<ItemPortfolio> itens = processos.findByAtivoTrue().stream()
                .map(processo -> {
                    InstrumentoContratual instrumento = instrumentoPorProcesso.get(processo.getId());
                    return new ItemPortfolio(
                            processo, instrumento, status(processo, instrumento, hoje));
                })
                .filter(item -> contem(item.processo().getOrigem(), origem))
                .filter(item -> tipo == null
                        || item.instrumento() != null && item.instrumento().getTipo() == tipo)
                .filter(item -> statusSelecionado == null || item.status() == statusSelecionado)
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
                .filter(i -> sicc.situacao(i.getVigenciaContratualFinal()) == SituacaoVigencia.PROXIMA_VENCIMENTO).count();
        long alertasTed = portfolio.stream()
                .filter(i -> sicc.situacao(i.getVigenciaTedFinal()) == SituacaoVigencia.PROXIMA_VENCIMENTO).count();
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

    @Transactional
    public RelatorioResponse gerar(GerarRelatorioRequest request, UsuarioInterno autor, String ip) {
        request = new GerarRelatorioRequest(
                request.tipo(), request.formato(), normalizarFiltros(request.tipo(), request.filtros()));
        LocalDateTime geradoEm = LocalDateTime.now(clock);
        String csv = cabecalho(request, autor, geradoEm) + construirCsv(request);
        byte[] content = switch (request.formato()) {
            case CSV -> csv.getBytes(StandardCharsets.UTF_8);
            case PDF -> pdf(csv);
            case XLSX -> xlsx(csv);
        };
        RelatorioGerado relatorio = new RelatorioGerado();
        relatorio.setTipo(request.tipo());
        relatorio.setFormato(request.formato());
        relatorio.setFiltros(serializarFiltros(request.filtros()));
        relatorio.setChaveArmazenamento(storage.armazenar(content,
                "relatorios/" + request.tipo().name().toLowerCase(Locale.ROOT)));
        relatorio.setChecksumSha256(ChecksumArquivo.sha256(content));
        relatorio.setTamanhoBytes((long) content.length);
        relatorio.setCriadoPor(autor);
        relatorio.setCriadoEm(geradoEm);
        relatorios.save(relatorio);
        auditoria.registrar(autor, "GERAR_RELATORIO", "RELATORIO", relatorio.getId(), true,
                request.tipo() + "/" + request.formato(), ip);
        return response(relatorio);
    }

    @Transactional(readOnly = true)
    public List<RelatorioResponse> listar() {
        return relatorios.findAllByOrderByCriadoEmDesc().stream().map(this::response).toList();
    }

    @Transactional
    public Download download(Long id, UsuarioInterno autor, String ip) {
        RelatorioGerado r = relatorios.findById(id)
                .orElseThrow(() -> new NotFoundException("Relatório não encontrado."));
        String mime = switch (r.getFormato()) {
            case CSV -> "text/csv;charset=UTF-8";
            case PDF -> "application/pdf";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        };
        Resource resource = storage.carregar(r.getChaveArmazenamento());
        auditoria.registrar(autor, "DOWNLOAD_RELATORIO", "RELATORIO", id, true, null, ip);
        return new Download(resource, nomeArquivo(r), mime);
    }

    private String construirCsv(GerarRelatorioRequest request) {
        List<ProcessoAdministrativo> filtrados = processosFiltrados(request.filtros());
        return switch (request.tipo()) {
            case ANUAL_PROCESSOS -> relatorioAnual(filtrados, request.filtros());
            case INSTRUMENTOS_POR_TIPO -> instrumentosPorTipo(filtrados);
            case HISTORICO_TRAMITACOES -> historicoTramitacoes(request.filtros(), filtrados);
            case VIGENCIAS -> vigencias(filtrados);
            case CONSOLIDADO -> consolidado(filtrados);
        };
    }

    private String cabecalho(
            GerarRelatorioRequest request, UsuarioInterno autor, LocalDateTime geradoEm) {
        return "relatorio;" + titulo(request.tipo()) + "\n"
                + "filtros;" + formatarFiltros(request.filtros()) + "\n"
                + "autor;" + esc(autor.getNome()) + " (" + esc(autor.getLogin()) + ")\n"
                + "gerado_em;" + DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(geradoEm)
                + "\n\n";
    }

    private String titulo(com.moments.sicc.domain.Enums.TipoRelatorio tipo) {
        return switch (tipo) {
            case ANUAL_PROCESSOS -> "Relatório anual de processos";
            case INSTRUMENTOS_POR_TIPO -> "Relatório de instrumentos por tipo";
            case HISTORICO_TRAMITACOES -> "Relatório do histórico de tramitações";
            case VIGENCIAS -> "Relatório de vigências";
            case CONSOLIDADO -> "Relatório consolidado";
        };
    }

    private String formatarFiltros(Map<String, String> filtros) {
        if (filtros == null || filtros.isEmpty()) return "Sem filtros";
        return new TreeMap<>(filtros).entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> esc(entry.getKey()) + "=" + esc(entry.getValue().trim()))
                .collect(Collectors.joining(" | "));
    }

    private String consolidado(List<ProcessoAdministrativo> filtrados) {
        StringBuilder csv = new StringBuilder("numero_processo;origem;status;tipo_instrumento;coordenador;vigencia_contratual;vigencia_ted;valor_atual\n");
        LocalDate hoje = LocalDate.now(clock);
        for (ProcessoAdministrativo p : filtrados) {
            InstrumentoContratual i = instrumentos.findByProcessoId(p.getId()).orElse(null);
            csv.append(esc(p.getNumero())).append(';').append(esc(p.getOrigem())).append(';')
                    .append(status(p, i, hoje)).append(';');
            if (i != null) {
                csv.append(i.getTipo()).append(';').append(esc(i.getCoordenador())).append(';')
                        .append(i.getVigenciaContratualFinal()).append(';')
                        .append(i.getVigenciaTedFinal() == null ? "" : i.getVigenciaTedFinal()).append(';')
                        .append(i.getValorAtual());
            } else csv.append(";;;;;");
            csv.append('\n');
        }
        return csv.toString();
    }

    private String relatorioAnual(List<ProcessoAdministrativo> filtrados, Map<String, String> filtros) {
        StringBuilder csv = new StringBuilder("ano;total;em_formalizacao;em_vigencia;concluido\n");
        LocalDate hoje = LocalDate.now(clock);
        Map<Integer, EnumMap<StatusProcesso, Long>> porAno = new java.util.TreeMap<>();
        filtrados.stream()
                .filter(p -> filtro(filtros, "ano") == null
                        || Integer.toString(p.getDataCadastro().getYear()).equals(filtro(filtros, "ano")))
                .forEach(p -> {
                    EnumMap<StatusProcesso, Long> contagem = porAno.computeIfAbsent(
                            p.getDataCadastro().getYear(), ignored -> new EnumMap<>(StatusProcesso.class));
                    InstrumentoContratual i = instrumentos.findByProcessoId(p.getId()).orElse(null);
                    contagem.merge(status(p, i, hoje), 1L, Long::sum);
                });
        porAno.forEach((ano, contagem) -> csv.append(ano).append(';')
                .append(contagem.values().stream().mapToLong(Long::longValue).sum()).append(';')
                .append(contagem.getOrDefault(StatusProcesso.EM_FORMALIZACAO, 0L)).append(';')
                .append(contagem.getOrDefault(StatusProcesso.EM_VIGENCIA, 0L)).append(';')
                .append(contagem.getOrDefault(StatusProcesso.CONCLUIDO, 0L)).append('\n'));
        return csv.toString();
    }

    private String instrumentosPorTipo(List<ProcessoAdministrativo> filtrados) {
        StringBuilder csv = new StringBuilder("tipo_instrumento;quantidade;valor_total_atual\n");
        Map<TipoInstrumento, List<InstrumentoContratual>> grupos = new EnumMap<>(TipoInstrumento.class);
        filtrados.stream()
                .map(p -> instrumentos.findByProcessoId(p.getId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .forEach(i -> grupos.computeIfAbsent(i.getTipo(), ignored -> new ArrayList<>()).add(i));
        grupos.forEach((tipo, lista) -> csv.append(tipo).append(';').append(lista.size()).append(';')
                .append(lista.stream().map(InstrumentoContratual::getValorAtual)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .append('\n'));
        return csv.toString();
    }

    private String historicoTramitacoes(
            Map<String, String> filtros, List<ProcessoAdministrativo> processosFiltrados) {
        StringBuilder csv = new StringBuilder(
                "numero_processo;contexto;contexto_id;data;sequencia;setor_destino;autor;observacao;"
                        + "permanencia_inicio;permanencia_fim;permanencia_dias;permanencia_aberta\n");
        Map<Long, ProcessoAdministrativo> processosPermitidos = processosFiltrados.stream()
                .collect(Collectors.toMap(ProcessoAdministrativo::getId, processo -> processo));
        List<Movimentacao> percursoCompleto = movimentacoes.findAll().stream()
                .filter(m -> processoDaMovimentacao(m, processosPermitidos) != null)
                .sorted(java.util.Comparator.comparing(Movimentacao::getContextoTipo)
                        .thenComparing(Movimentacao::getContextoId)
                        .thenComparing(Movimentacao::getDataMovimentacao)
                        .thenComparing(Movimentacao::getSequenciaDiaria))
                .toList();
        Map<Long, PermanenciaCalculada> permanenciasPorMovimento = new java.util.HashMap<>();
        percursoCompleto.stream().collect(Collectors.groupingBy(
                        movimento -> new ContextoMovimentacao(
                                movimento.getContextoTipo(), movimento.getContextoId()),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .values().forEach(percurso -> {
                    calcularPermanenciasDoPercurso(
                            percurso, LocalDate.now(clock)).forEach(permanencia ->
                            permanenciasPorMovimento.put(
                                    permanencia.movimentacaoChegadaId(), permanencia));
                });
        for (Movimentacao atual : percursoCompleto) {
            if (filtro(filtros, "contexto") != null
                    && !atual.getContextoTipo().name().equals(filtro(filtros, "contexto"))) continue;
            PermanenciaCalculada permanencia = permanenciasPorMovimento.get(atual.getId());
            boolean periodoAbertoNoIntervalo = permanencia != null && permanencia.aberta()
                    && permanenciaIntersecaIntervalo(permanencia, filtros);
            if (!dataNoIntervalo(atual.getDataMovimentacao(), filtros)
                    && !periodoAbertoNoIntervalo) continue;
            ProcessoAdministrativo processo = processoDaMovimentacao(atual, processosPermitidos);
            csv.append(esc(processo.getNumero())).append(';')
                    .append(atual.getContextoTipo()).append(';').append(atual.getContextoId()).append(';')
                    .append(atual.getDataMovimentacao()).append(';').append(atual.getSequenciaDiaria()).append(';')
                    .append(esc(atual.getSetorDestino().getSigla())).append(';')
                    .append(esc(atual.getAutor().getNome())).append(';')
                    .append(esc(atual.getObservacao())).append(';')
                    .append(permanencia == null ? "" : permanencia.dataChegada()).append(';')
                    .append(permanencia == null || permanencia.dataSaida() == null
                            ? "" : permanencia.dataSaida()).append(';')
                    .append(permanencia == null ? "" : permanencia.diasCorridos()).append(';')
                    .append(permanencia == null ? "" : permanencia.aberta()).append('\n');
        }
        return csv.toString();
    }

    private boolean permanenciaIntersecaIntervalo(
            PermanenciaCalculada permanencia, Map<String, String> filtros) {
        LocalDate inicio = filtro(filtros, "dataInicial") == null
                ? null : LocalDate.parse(filtro(filtros, "dataInicial"));
        LocalDate fim = filtro(filtros, "dataFinal") == null
                ? null : LocalDate.parse(filtro(filtros, "dataFinal"));
        LocalDate fimPermanencia = permanencia.dataSaida() == null
                ? LocalDate.now(clock) : permanencia.dataSaida();
        return (fim == null || !permanencia.dataChegada().isAfter(fim))
                && (inicio == null || !fimPermanencia.isBefore(inicio));
    }

    private ProcessoAdministrativo processoDaMovimentacao(
            Movimentacao movimentacao, Map<Long, ProcessoAdministrativo> processosPermitidos) {
        if (movimentacao.getContextoTipo() == ContextoTramitacao.FORMALIZACAO) {
            return processosPermitidos.get(movimentacao.getContextoId());
        }
        return alteracoes.findById(movimentacao.getContextoId())
                .map(a -> processosPermitidos.get(a.getInstrumento().getProcesso().getId()))
                .orElse(null);
    }

    private String vigencias(List<ProcessoAdministrativo> filtrados) {
        StringBuilder csv = new StringBuilder(
                "numero_processo;tipo_instrumento;vigencia_contratual;situacao_contratual;"
                        + "dias_ate_vencimento_contratual;no_horizonte_120_contratual;vigencia_ted;"
                        + "situacao_ted;dias_ate_vencimento_ted;no_horizonte_120_ted\n");
        LocalDate hoje = LocalDate.now(clock);
        filtrados.forEach(p -> instrumentos.findByProcessoId(p.getId()).ifPresent(i -> csv
                .append(esc(p.getNumero())).append(';').append(i.getTipo()).append(';')
                .append(i.getVigenciaContratualFinal()).append(';')
                .append(sicc.situacao(i.getVigenciaContratualFinal())).append(';')
                .append(diasAteVencimento(hoje, i.getVigenciaContratualFinal())).append(';')
                .append(noHorizonteDeAlerta(i.getVigenciaContratualFinal())).append(';')
                .append(i.getVigenciaTedFinal() == null ? "" : i.getVigenciaTedFinal()).append(';')
                .append(sicc.situacao(i.getVigenciaTedFinal())).append(';')
                .append(diasAteVencimento(hoje, i.getVigenciaTedFinal())).append(';')
                .append(noHorizonteDeAlerta(i.getVigenciaTedFinal())).append('\n')));
        return csv.toString();
    }

    private String diasAteVencimento(LocalDate hoje, LocalDate vencimento) {
        return vencimento == null ? "" : Long.toString(ChronoUnit.DAYS.between(hoje, vencimento));
    }

    private boolean noHorizonteDeAlerta(LocalDate vencimento) {
        return sicc.situacao(vencimento) == SituacaoVigencia.PROXIMA_VENCIMENTO;
    }

    private List<ProcessoAdministrativo> processosFiltrados(Map<String, String> filtros) {
        LocalDate hoje = LocalDate.now(clock);
        return processos.findByAtivoTrue().stream()
                .filter(p -> contem(p.getNumero(), filtro(filtros, "numero")))
                .filter(p -> contem(p.getOrigem(), filtro(filtros, "origem")))
                .filter(p -> {
                    InstrumentoContratual i = instrumentos.findByProcessoId(p.getId()).orElse(null);
                    String tipo = filtro(filtros, "tipo");
                    String status = filtro(filtros, "status");
                    String vigenciaContratual = filtro(filtros, "vigenciaContratual");
                    String vigenciaTed = filtro(filtros, "vigenciaTed");
                    return (tipo == null || i != null && i.getTipo().name().equals(tipo))
                            && (status == null || status(p, i, hoje).name().equals(status))
                            && (vigenciaContratual == null || i != null
                            && sicc.situacao(i.getVigenciaContratualFinal()).name()
                            .equals(vigenciaContratual))
                            && (vigenciaTed == null || i != null
                            && sicc.situacao(i.getVigenciaTedFinal()).name().equals(vigenciaTed));
                })
                .toList();
    }

    private StatusProcesso status(
            ProcessoAdministrativo processo, InstrumentoContratual instrumento, LocalDate hoje) {
        if (instrumento == null) return StatusProcesso.EM_FORMALIZACAO;
        return instrumento.getVigenciaContratualFinal().isBefore(hoje)
                ? StatusProcesso.CONCLUIDO : StatusProcesso.EM_VIGENCIA;
    }

    private boolean dataNoIntervalo(LocalDate data, Map<String, String> filtros) {
        String inicio = filtro(filtros, "dataInicial");
        String fim = filtro(filtros, "dataFinal");
        return (inicio == null || !data.isBefore(LocalDate.parse(inicio)))
                && (fim == null || !data.isAfter(LocalDate.parse(fim)));
    }

    private boolean contem(String valor, String trecho) {
        return trecho == null || valor.toLowerCase(Locale.ROOT).contains(trecho.toLowerCase(Locale.ROOT));
    }

    private String filtro(Map<String, String> filtros, String nome) {
        if (filtros == null) return null;
        String valor = filtros.get(nome);
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private Permanencia calcularPermanencias(List<ProcessoAdministrativo> ativos, LocalDate hoje) {
        Map<String, List<PermanenciaProcessoDashboardResponse>> detalhes = new TreeMap<>();
        for (ProcessoAdministrativo p : ativos) {
            List<Movimentacao> lista = movimentacoes
                    .findByContextoTipoAndContextoIdOrderByDataMovimentacaoAscSequenciaDiariaAsc(
                            ContextoTramitacao.FORMALIZACAO, p.getId());
            calcularPermanenciasDoPercurso(lista, hoje).forEach(permanencia ->
                    detalhes.computeIfAbsent(permanencia.setor(), key -> new ArrayList<>())
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

    private List<PermanenciaCalculada> calcularPermanenciasDoPercurso(
            List<Movimentacao> percurso, LocalDate hoje) {
        List<PermanenciaCalculada> permanencias = new ArrayList<>();
        Movimentacao chegada = null;
        for (Movimentacao movimento : percurso) {
            if (chegada == null) {
                chegada = movimento;
            } else if (!chegada.getSetorDestino().getId()
                    .equals(movimento.getSetorDestino().getId())) {
                permanencias.add(permanenciaCalculada(
                        chegada, movimento.getDataMovimentacao(), false));
                chegada = movimento;
            }
        }
        if (chegada != null) permanencias.add(permanenciaCalculada(chegada, hoje, true));
        return permanencias;
    }

    private PermanenciaCalculada permanenciaCalculada(
            Movimentacao chegada, LocalDate fimPermanencia, boolean aberta) {
        return new PermanenciaCalculada(
                chegada.getId(), chegada.getSetorDestino().getSigla(), chegada.getDataMovimentacao(),
                aberta ? null : fimPermanencia,
                Math.max(0, ChronoUnit.DAYS.between(
                        chegada.getDataMovimentacao(), fimPermanencia)), aberta);
    }

    private byte[] pdf(String text) {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = Files.newInputStream(localizarFonteUnicode())) {
            PDType0Font font = PDType0Font.load(document, fontStream, true);
            List<String> linhas = linhasPdf(text);
            for (int inicio = 0; inicio < linhas.size(); inicio += 72) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 8);
                    content.newLineAtOffset(40, 800);
                    for (String linha : linhas.subList(inicio, Math.min(inicio + 72, linhas.size()))) {
                        content.showText(linha);
                        content.newLineAtOffset(0, -10);
                    }
                    content.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o PDF do relatório.", e);
        }
    }

    private Path localizarFonteUnicode() {
        List<Path> candidatas = new ArrayList<>();
        String configurada = System.getProperty("sicc.relatorio.font-path");
        if (configurada != null && !configurada.isBlank()) candidatas.add(Path.of(configurada));
        String windows = System.getenv("WINDIR");
        if (windows != null && !windows.isBlank()) {
            candidatas.add(Path.of(windows, "Fonts", "DejaVuSans.ttf"));
            candidatas.add(Path.of(windows, "Fonts", "arial.ttf"));
        }
        candidatas.add(Path.of(System.getProperty("java.home"), "lib", "fonts", "DejaVuSans.ttf"));
        candidatas.add(Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
        candidatas.add(Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"));
        candidatas.add(Path.of("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"));
        return candidatas.stream().filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Fonte TrueType Unicode não encontrada; configure sicc.relatorio.font-path."));
    }

    private List<String> linhasPdf(String text) {
        List<String> linhas = new ArrayList<>();
        for (String original : text.replace("\r", "").split("\n", -1)) {
            String restante = original;
            do {
                if (restante.length() <= 110) {
                    linhas.add(restante);
                    restante = "";
                } else {
                    int quebra = Math.max(restante.lastIndexOf(';', 110), restante.lastIndexOf(' ', 110));
                    if (quebra < 50) quebra = 110;
                    boolean separadorDescartavel = restante.charAt(quebra) == ' ';
                    linhas.add(restante.substring(0, quebra + (separadorDescartavel ? 0 : 1)));
                    restante = restante.substring(quebra + 1).stripLeading();
                }
            } while (!restante.isEmpty());
        }
        return linhas;
    }

    private byte[] xlsx(String csv) {
        try {
            StringBuilder rows = new StringBuilder();
            int rowNumber = 1;
            for (String line : csv.split("\\n")) {
                rows.append("<row r=\"").append(rowNumber++).append("\">");
                for (String value : line.split(";", -1)) {
                    rows.append("<c t=\"inlineStr\"><is><t>").append(xml(value)).append("</t></is></c>");
                }
                rows.append("</row>");
            }
            Map<String, String> entries = Map.of(
                    "[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>",
                    "_rels/.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
                    "xl/workbook.xml", "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"SICC\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
                    "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>",
                    "xl/worksheets/sheet1.xml", "<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>" + rows + "</sheetData></worksheet>");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (var entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar XLSX.", e);
        }
    }

    private String esc(String value) {
        return value == null ? "" : value.replace(";", ",").replace("\n", " ");
    }

    private String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private RelatorioResponse response(RelatorioGerado r) {
        UsuarioInterno autor = r.getCriadoPor();
        return new RelatorioResponse(
                r.getId(), r.getTipo(), r.getFormato(), desserializarFiltros(r.getFiltros()),
                new AtorAuditoriaResponse(autor.getId(), autor.getLogin(), autor.getNome()),
                r.getCriadoEm(), r.getChecksumSha256(), r.getChaveArmazenamento(),
                r.getTamanhoBytes() == null ? 0 : r.getTamanhoBytes(), nomeArquivo(r));
    }

    private String serializarFiltros(Map<String, String> filtros) {
        try {
            return objectMapper.writeValueAsString(
                    filtros == null ? Map.of() : new TreeMap<>(filtros));
        } catch (Exception e) {
            throw new IllegalArgumentException("Filtros de relatório inválidos.", e);
        }
    }

    private Map<String, String> normalizarFiltros(
            TipoRelatorio tipo, Map<String, String> filtros) {
        if (filtros == null || filtros.isEmpty()) return Map.of();
        Map<String, String> normalizados = new TreeMap<>();
        filtros.forEach((nome, valor) -> {
            if (nome != null && !nome.isBlank() && valor != null && !valor.isBlank()) {
                normalizados.put(nome.trim(), valor.trim());
            }
        });
        Set<String> comuns = Set.of(
                "numero", "origem", "tipo", "status", "vigenciaContratual", "vigenciaTed");
        Set<String> permitidos = new java.util.HashSet<>(comuns);
        switch (tipo) {
            case ANUAL_PROCESSOS -> permitidos.add("ano");
            case HISTORICO_TRAMITACOES -> permitidos.addAll(
                    Set.of("contexto", "dataInicial", "dataFinal"));
            default -> { }
        }
        List<String> naoAplicaveis = normalizados.keySet().stream()
                .filter(nome -> !permitidos.contains(nome)).sorted().toList();
        if (!naoAplicaveis.isEmpty()) {
            throw new DomainException("Filtros não aplicáveis a " + tipo + ": "
                    + String.join(", ", naoAplicaveis) + ".");
        }
        validarValoresDosFiltros(normalizados);
        return java.util.Collections.unmodifiableMap(normalizados);
    }

    private void validarValoresDosFiltros(Map<String, String> filtros) {
        try {
            if (filtros.containsKey("ano")) Integer.parseInt(filtros.get("ano"));
            if (filtros.containsKey("tipo")) TipoInstrumento.valueOf(filtros.get("tipo"));
            if (filtros.containsKey("status")) StatusProcesso.valueOf(filtros.get("status"));
            if (filtros.containsKey("vigenciaContratual")) {
                SituacaoVigencia.valueOf(filtros.get("vigenciaContratual"));
            }
            if (filtros.containsKey("vigenciaTed")) {
                SituacaoVigencia.valueOf(filtros.get("vigenciaTed"));
            }
            if (filtros.containsKey("contexto")) ContextoTramitacao.valueOf(filtros.get("contexto"));
            LocalDate inicio = filtros.containsKey("dataInicial")
                    ? LocalDate.parse(filtros.get("dataInicial")) : null;
            LocalDate fim = filtros.containsKey("dataFinal")
                    ? LocalDate.parse(filtros.get("dataFinal")) : null;
            if (inicio != null && fim != null && inicio.isAfter(fim)) {
                throw new DomainException("O filtro dataInicial não pode ser posterior a dataFinal.");
            }
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DomainException("Valor de filtro de relatório inválido.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> desserializarFiltros(String filtros) {
        if (filtros == null || filtros.isBlank()) return Map.of();
        try {
            return java.util.Collections.unmodifiableMap(
                    new TreeMap<>(objectMapper.readValue(filtros, Map.class)));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String nomeArquivo(RelatorioGerado relatorio) {
        String ext = relatorio.getFormato().name().toLowerCase(Locale.ROOT);
        return relatorio.getTipo().name().toLowerCase(Locale.ROOT)
                + "-" + relatorio.getId() + "." + ext;
    }

    private record Permanencia(
            Map<String, Double> medias,
            String gargalo,
            Map<String, List<PermanenciaProcessoDashboardResponse>> detalhes) {}
    private record ContextoMovimentacao(ContextoTramitacao tipo, Long id) {}
    private record PermanenciaCalculada(
            Long movimentacaoChegadaId, String setor, LocalDate dataChegada, LocalDate dataSaida,
            long diasCorridos, boolean aberta) {}
    private record ItemPortfolio(
            ProcessoAdministrativo processo,
            InstrumentoContratual instrumento,
            StatusProcesso status) {}
    public record Download(Resource resource, String filename, String mimeType) {}
}
