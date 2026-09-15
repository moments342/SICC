package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.RelatorioGerado;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.MovimentacaoRepository;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.repository.RelatorioGeradoRepository;
import com.moments.sicc.service.RegrasDeVigencia.ReferenciaDeVigencia;
import com.moments.sicc.shared.ChecksumArquivo;
import com.moments.sicc.shared.exception.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RelatorioDashboardService {
    private final ProcessoAdministrativoRepository processos;
    private final InstrumentoContratualRepository instrumentos;
    private final MovimentacaoRepository movimentacoes;
    private final RelatorioGeradoRepository relatorios;
    private final ArmazenamentoTransacional storage;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final CalculadoraPermanencia calculadoraPermanencia;
    private final RegrasDeVigencia regrasDeVigencia;
    private final GeradorArquivoRelatorio geradorArquivo;
    private final CatalogoFiltrosRelatorio catalogoFiltros;
    private final GeracaoRelatorios geracaoRelatorios;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(
            String origem, TipoInstrumento tipo, StatusProcesso statusSelecionado) {
        LocalDate hoje = LocalDate.now(clock);
        ReferenciaDeVigencia referencia = regrasDeVigencia.referenciaEm(hoje);
        Map<Long, InstrumentoContratual> instrumentoPorProcesso = instrumentos
                .findAllByProcessoAtivoTrue().stream()
                .collect(java.util.stream.Collectors.toMap(
                        instrumento -> instrumento.getProcesso().getId(),
                        instrumento -> instrumento));
        List<ItemPortfolio> itens = processos.findByAtivoTrue().stream()
                .map(processo -> {
                    InstrumentoContratual instrumento = instrumentoPorProcesso.get(processo.getId());
                    return new ItemPortfolio(
                            processo, instrumento, referencia.status(
                                    instrumento == null
                                            ? null : instrumento.getVigenciaContratualFinal()));
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

    @Transactional
    public RelatorioResponse gerar(GerarRelatorioRequest request, UsuarioInterno autor, String ip) {
        request = new GerarRelatorioRequest(
                request.tipo(), request.formato(),
                catalogoFiltros.normalizar(request.tipo(), request.filtros()));
        EmissaoRelatorio emissao = EmissaoRelatorio.iniciar(
                clock, regrasDeVigencia, autor);
        ConteudoRelatorio conteudo = geracaoRelatorios.gerar(
                request.tipo(),
                new GeracaoRelatorios.Contexto(
                        request.filtros(),
                        processosFiltrados(request.filtros(), emissao.referencia()),
                        emissao));
        byte[] content = geradorArquivo.gerar(request.formato(), conteudo);
        RelatorioGerado relatorio = new RelatorioGerado();
        relatorio.setTipo(request.tipo());
        relatorio.setFormato(request.formato());
        relatorio.setFiltros(catalogoFiltros.serializar(request.filtros()));
        relatorio.setChaveArmazenamento(storage.armazenar(content,
                "relatorios/" + request.tipo().name().toLowerCase(Locale.ROOT)));
        relatorio.setChecksumSha256(ChecksumArquivo.sha256(content));
        relatorio.setTamanhoBytes((long) content.length);
        relatorio.setCriadoPor(autor);
        relatorio.setCriadoEm(emissao.geradoEm());
        relatorios.save(relatorio);
        auditoria.registrarNaTransacaoAtual(
                autor, "GERAR_RELATORIO", "RELATORIO", relatorio.getId(), true,
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
        String mime = geradorArquivo.mime(r.getFormato());
        Resource resource = storage.carregar(r.getChaveArmazenamento());
        auditoria.registrarNaTransacaoAtual(
                autor, "DOWNLOAD_RELATORIO", "RELATORIO", id, true, null, ip);
        return new Download(resource, nomeArquivo(r), mime);
    }

    private List<ProcessoAdministrativo> processosFiltrados(
            Map<String, String> filtros, ReferenciaDeVigencia referencia) {
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
                            && (status == null || referencia.status(
                                    i == null ? null : i.getVigenciaContratualFinal())
                                    .name().equals(status))
                            && (vigenciaContratual == null || i != null
                            && referencia.situacao(i.getVigenciaContratualFinal()).name()
                            .equals(vigenciaContratual))
                            && (vigenciaTed == null || i != null
                            && referencia.situacao(i.getVigenciaTedFinal()).name()
                                    .equals(vigenciaTed));
                })
                .toList();
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
            calculadoraPermanencia.calcular(lista, hoje).forEach(permanencia ->
                    detalhes.computeIfAbsent(
                                    permanencia.setor().getSigla(), key -> new ArrayList<>())
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

    private RelatorioResponse response(RelatorioGerado r) {
        UsuarioInterno autor = r.getCriadoPor();
        return new RelatorioResponse(
                r.getId(), r.getTipo(), r.getFormato(),
                catalogoFiltros.desserializar(r.getFiltros()),
                new AtorAuditoriaResponse(autor.getId(), autor.getLogin(), autor.getNome()),
                r.getCriadoEm(), r.getChecksumSha256(), r.getChaveArmazenamento(),
                r.getTamanhoBytes() == null ? 0 : r.getTamanhoBytes(), nomeArquivo(r));
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
    private record ItemPortfolio(
            ProcessoAdministrativo processo,
            InstrumentoContratual instrumento,
            StatusProcesso status) {}
    public record Download(Resource resource, String filename, String mimeType) {}
}
