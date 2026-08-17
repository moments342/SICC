package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.FormatoRelatorio;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoInstrumento;
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
import com.moments.sicc.shared.exception.NotFoundException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RelatorioDashboardService {
    private final ProcessoAdministrativoRepository processos;
    private final InstrumentoContratualRepository instrumentos;
    private final AlteracaoContratualRepository alteracoes;
    private final MovimentacaoRepository movimentacoes;
    private final RelatorioGeradoRepository relatorios;
    private final StorageService storage;
    private final SiccService sicc;
    private final AuditoriaService auditoria;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        LocalDate hoje = LocalDate.now(clock);
        List<ProcessoAdministrativo> ativos = processos.findByAtivoTrue();
        List<InstrumentoContratual> portfolio = instrumentos.findAllByProcessoAtivoTrue();
        EnumMap<StatusProcesso, Long> porStatus = new EnumMap<>(StatusProcesso.class);
        for (StatusProcesso status : StatusProcesso.values()) porStatus.put(status, 0L);
        ativos.forEach(p -> {
            InstrumentoContratual i = instrumentos.findByProcessoId(p.getId()).orElse(null);
            StatusProcesso status = i == null ? StatusProcesso.EM_FORMALIZACAO
                    : i.getVigenciaContratualFinal().isBefore(hoje)
                            ? StatusProcesso.CONCLUIDO : StatusProcesso.EM_VIGENCIA;
            porStatus.merge(status, 1L, Long::sum);
        });
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
        portfolio.forEach(i -> porTipo.merge(i.getTipo(), 1L, Long::sum));
        Permanencia permanencia = calcularPermanencias(ativos, hoje);
        double tempoInicial = ativos.stream().mapToLong(p -> {
            LocalDate fim = instrumentos.findByProcessoId(p.getId())
                    .map(InstrumentoContratual::getDataFormalizacao).orElse(hoje);
            return Math.max(0, ChronoUnit.DAYS.between(p.getDataCadastro(), fim));
        }).average().orElse(0);
        Map<String, Long> formalizacoes = new LinkedHashMap<>();
        portfolio.forEach(i -> formalizacoes.merge(YearMonth.from(i.getDataFormalizacao()).toString(), 1L, Long::sum));
        Map<String, Long> conclusoes = new LinkedHashMap<>();
        portfolio.stream().filter(i -> i.getVigenciaContratualFinal().isBefore(hoje))
                .forEach(i -> conclusoes.merge(YearMonth.from(i.getVigenciaContratualFinal()).toString(), 1L, Long::sum));
        return new DashboardResponse(porStatus, percentual, alertasContrato, alertasTed, valor, porTipo,
                permanencia.medias(), permanencia.gargalo(), tempoInicial, formalizacoes, conclusoes);
    }

    @Transactional
    public RelatorioResponse gerar(GerarRelatorioRequest request, UsuarioInterno autor, String ip) {
        String csv = construirCsv(request);
        byte[] content = switch (request.formato()) {
            case CSV -> csv.getBytes(StandardCharsets.UTF_8);
            case PDF -> pdf(csv);
            case XLSX -> xlsx(csv);
        };
        RelatorioGerado relatorio = new RelatorioGerado();
        relatorio.setTipo(request.tipo());
        relatorio.setFormato(request.formato());
        try {
            relatorio.setFiltros(request.filtros() == null ? "{}" : objectMapper.writeValueAsString(request.filtros()));
        } catch (Exception e) {
            relatorio.setFiltros("{}");
        }
        relatorio.setChaveArmazenamento(storage.armazenar(content,
                "relatorios/" + request.tipo().name().toLowerCase(Locale.ROOT)));
        relatorio.setCriadoPor(autor);
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
        String ext = r.getFormato().name().toLowerCase(Locale.ROOT);
        String mime = switch (r.getFormato()) {
            case CSV -> "text/csv";
            case PDF -> "application/pdf";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        };
        auditoria.registrar(autor, "DOWNLOAD_RELATORIO", "RELATORIO", id, true, null, ip);
        return new Download(storage.carregar(r.getChaveArmazenamento()),
                r.getTipo().name().toLowerCase(Locale.ROOT) + "-" + r.getId() + "." + ext, mime);
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
                "contexto;contexto_id;data;sequencia;setor_destino;autor;observacao;permanencia_ate_proxima_dias\n");
        Set<Long> processosPermitidos = processosFiltrados.stream()
                .map(ProcessoAdministrativo::getId).collect(java.util.stream.Collectors.toSet());
        List<Movimentacao> lista = movimentacoes.findAll().stream()
                .filter(m -> pertenceAProcessoPermitido(m, processosPermitidos))
                .filter(m -> filtro(filtros, "contexto") == null
                        || m.getContextoTipo().name().equals(filtro(filtros, "contexto")))
                .filter(m -> dataNoIntervalo(m.getDataMovimentacao(), filtros))
                .sorted(java.util.Comparator.comparing(Movimentacao::getContextoTipo)
                        .thenComparing(Movimentacao::getContextoId)
                        .thenComparing(Movimentacao::getDataMovimentacao)
                        .thenComparing(Movimentacao::getSequenciaDiaria))
                .toList();
        for (int index = 0; index < lista.size(); index++) {
            Movimentacao atual = lista.get(index);
            Movimentacao anterior = index > 0 ? lista.get(index - 1) : null;
            boolean novaChegada = anterior == null
                    || anterior.getContextoTipo() != atual.getContextoTipo()
                    || !anterior.getContextoId().equals(atual.getContextoId())
                    || !anterior.getSetorDestino().getId().equals(atual.getSetorDestino().getId());
            LocalDate fimPermanencia = LocalDate.now(clock);
            if (novaChegada) {
                for (int proximoIndex = index + 1; proximoIndex < lista.size(); proximoIndex++) {
                    Movimentacao candidata = lista.get(proximoIndex);
                    if (candidata.getContextoTipo() != atual.getContextoTipo()
                            || !candidata.getContextoId().equals(atual.getContextoId())) break;
                    if (!candidata.getSetorDestino().getId().equals(atual.getSetorDestino().getId())) {
                        fimPermanencia = candidata.getDataMovimentacao();
                        break;
                    }
                }
            }
            String permanencia = novaChegada
                    ? Long.toString(Math.max(0, ChronoUnit.DAYS.between(
                            atual.getDataMovimentacao(), fimPermanencia)))
                    : "";
            csv.append(atual.getContextoTipo()).append(';').append(atual.getContextoId()).append(';')
                    .append(atual.getDataMovimentacao()).append(';').append(atual.getSequenciaDiaria()).append(';')
                    .append(esc(atual.getSetorDestino().getSigla())).append(';')
                    .append(esc(atual.getAutor().getNome())).append(';')
                    .append(esc(atual.getObservacao())).append(';').append(permanencia).append('\n');
        }
        return csv.toString();
    }

    private boolean pertenceAProcessoPermitido(Movimentacao movimentacao, Set<Long> processosPermitidos) {
        if (movimentacao.getContextoTipo() == ContextoTramitacao.FORMALIZACAO) {
            return processosPermitidos.contains(movimentacao.getContextoId());
        }
        return alteracoes.findById(movimentacao.getContextoId())
                .map(a -> processosPermitidos.contains(a.getInstrumento().getProcesso().getId()))
                .orElse(false);
    }

    private String vigencias(List<ProcessoAdministrativo> filtrados) {
        StringBuilder csv = new StringBuilder(
                "numero_processo;tipo_instrumento;vigencia_contratual;situacao_contratual;vigencia_ted;situacao_ted\n");
        filtrados.forEach(p -> instrumentos.findByProcessoId(p.getId()).ifPresent(i -> csv
                .append(esc(p.getNumero())).append(';').append(i.getTipo()).append(';')
                .append(i.getVigenciaContratualFinal()).append(';')
                .append(sicc.situacao(i.getVigenciaContratualFinal())).append(';')
                .append(i.getVigenciaTedFinal() == null ? "" : i.getVigenciaTedFinal()).append(';')
                .append(sicc.situacao(i.getVigenciaTedFinal())).append('\n')));
        return csv.toString();
    }

    private List<ProcessoAdministrativo> processosFiltrados(Map<String, String> filtros) {
        LocalDate hoje = LocalDate.now(clock);
        return processos.findAll().stream()
                .filter(p -> contem(p.getNumero(), filtro(filtros, "numero")))
                .filter(p -> contem(p.getOrigem(), filtro(filtros, "origem")))
                .filter(p -> {
                    InstrumentoContratual i = instrumentos.findByProcessoId(p.getId()).orElse(null);
                    String tipo = filtro(filtros, "tipo");
                    String status = filtro(filtros, "status");
                    String vigencia = filtro(filtros, "vigencia");
                    return (tipo == null || i != null && i.getTipo().name().equals(tipo))
                            && (status == null || status(p, i, hoje).name().equals(status))
                            && (vigencia == null || i != null
                            && (sicc.situacao(i.getVigenciaContratualFinal()).name().equals(vigencia)
                            || sicc.situacao(i.getVigenciaTedFinal()).name().equals(vigencia)));
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
        Map<String, List<Long>> dias = new HashMap<>();
        for (ProcessoAdministrativo p : ativos) {
            List<Movimentacao> lista = movimentacoes
                    .findByContextoTipoAndContextoIdOrderByDataMovimentacaoAscSequenciaDiariaAsc(
                            ContextoTramitacao.FORMALIZACAO, p.getId());
            String setor = null;
            LocalDate chegada = null;
            for (Movimentacao m : lista) {
                String destino = m.getSetorDestino().getSigla();
                if (setor == null) {
                    setor = destino;
                    chegada = m.getDataMovimentacao();
                } else if (!setor.equals(destino)) {
                    dias.computeIfAbsent(setor, key -> new ArrayList<>())
                            .add(Math.max(0, ChronoUnit.DAYS.between(chegada, m.getDataMovimentacao())));
                    setor = destino;
                    chegada = m.getDataMovimentacao();
                }
            }
            if (setor != null) dias.computeIfAbsent(setor, key -> new ArrayList<>())
                    .add(Math.max(0, ChronoUnit.DAYS.between(chegada, hoje)));
        }
        Map<String, Double> medias = new LinkedHashMap<>();
        dias.forEach((setor, values) -> medias.put(setor, values.stream().mapToLong(Long::longValue).average().orElse(0)));
        String gargalo = medias.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        return new Permanencia(medias, gargalo);
    }

    private byte[] pdf(String text) {
        String safe = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                .replace("\r", "").replace("\n", ") Tj T* (");
        String stream = "BT /F1 8 Tf 40 800 Td 10 TL (" + safe + ") Tj ET";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + stream + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        StringBuilder out = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            out.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = out.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        out.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        offsets.forEach(offset -> out.append(String.format("%010d 00000 n \n", offset)));
        out.append("trailer << /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF");
        return out.toString().getBytes(StandardCharsets.ISO_8859_1);
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
        return new RelatorioResponse(r.getId(), r.getTipo(), r.getFormato(), r.getFiltros(), r.getCriadoEm());
    }

    private record Permanencia(Map<String, Double> medias, String gargalo) {}
    public record Download(Resource resource, String filename, String mimeType) {}
}
