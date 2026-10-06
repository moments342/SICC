import { useEffect, useRef, useState } from "react";
import { download, request } from "../api";
import { useResource } from "../useResource";
import { ResourceState } from "../components/Feedback";
import { useReportGeneration } from "../components/ReportGeneration";
import { opcoesDominio, rotuloDominio } from "../domainLabels";
import { CATALOGO_RELATORIOS, filtrosDoRelatorio, rotuloFiltroRelatorio } from "../reportCatalog";
import type { FormatoRelatorio, TipoRelatorio } from "../domain";
import type { AuthenticatedPageProps, RelatorioGerado } from "../models";

function filterHint(name: string) {
  const reports = CATALOGO_RELATORIOS.filter(report => report.filtros.includes(name));
  return reports.length === CATALOGO_RELATORIOS.length ? "Aplica-se a todos os relatórios."
    : `Aplica-se somente a: ${reports.map(report => report.rotulo).join(", ")}.`;
}

function describeFilters(filters: Record<string, string>) {
  return Object.entries(filters).map(([name, value]) => `${rotuloFiltroRelatorio(name)}: ${rotuloDominio(value)}`).join(" · ");
}

export function Reports({ token, notify }: AuthenticatedPageProps) {
  const generation = useReportGeneration();
  const notifiedRevision = useRef(generation.revision);
  useEffect(() => {
    if (notifiedRevision.current === generation.revision) return;
    notifiedRevision.current = generation.revision;
    notify(generation.error || "Relatório gerado e retido para download.", generation.error ? "error" : "success");
  }, [generation.revision, generation.error, notify]);
  const history = useResource<RelatorioGerado[]>("/api/v1/relatorios", token, generation.revision);
  const items = history.data ?? [];
  const [filters, setFilters] = useState<Record<string, string>>({});
  const filterProps = (name: string) => ({
    "aria-label": rotuloFiltroRelatorio(name), "aria-describedby": `report-filter-${name}-hint`
  });
  const hint = (name: string) => <small className="muted" id={`report-filter-${name}-hint`}>{filterHint(name)}</small>;
  function generationKey(type: TipoRelatorio, format: FormatoRelatorio) {
    return JSON.stringify([type, format, Object.entries(filtrosDoRelatorio(type, filters)).sort(([a], [b]) => a.localeCompare(b))]);
  }
  async function generate(type: TipoRelatorio, format: FormatoRelatorio) {
    await generation.run(generationKey(type, format), `${rotuloDominio(type)} · ${format}`, async () => {
      await request("/api/v1/relatorios", { method: "POST", body: JSON.stringify({
        tipo: type, formato: format,
        filtros: filtrosDoRelatorio(type, filters)
      }) }, token);
    });
  }
  return <section className="panel reports-panel"><h2>Filtros dos relatórios</h2>
    <div className="inline-form">{["numero", "origem", "ano", "dataInicial", "dataFinal"].map(name =>
      <label key={name}>{rotuloFiltroRelatorio(name)}<input {...filterProps(name)} type={name.startsWith("data") ? "date" : "text"} value={filters[name] ?? ""}
        onChange={e => setFilters({ ...filters, [name]: e.target.value })} />{hint(name)}</label>)}
      <label>{rotuloFiltroRelatorio("tipo")}<select {...filterProps("tipo")} value={filters.tipo ?? ""} onChange={e => setFilters({ ...filters, tipo: e.target.value })}>
        <option value="">Todos</option>{opcoesDominio("tipoInstrumento").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select>{hint("tipo")}</label>
      <label>{rotuloFiltroRelatorio("contexto")}<select {...filterProps("contexto")} value={filters.contexto ?? ""}
        onChange={e => setFilters({ ...filters, contexto: e.target.value })}>
        <option value="">Todos</option>
        {opcoesDominio("contextoTramitacao").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}
      </select>{hint("contexto")}</label>
      <label>{rotuloFiltroRelatorio("status")}<select {...filterProps("status")} value={filters.status ?? ""} onChange={e => setFilters({ ...filters, status: e.target.value })}>
        <option value="">Todos</option>{opcoesDominio("statusProcesso").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select>{hint("status")}</label>
      <label>{rotuloFiltroRelatorio("vigenciaContratual")}<select {...filterProps("vigenciaContratual")} value={filters.vigenciaContratual ?? ""}
        onChange={e => setFilters({ ...filters, vigenciaContratual: e.target.value })}>
        <option value="">Todas</option>{opcoesDominio("situacaoVigencia").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select>{hint("vigenciaContratual")}</label>
      <label>{rotuloFiltroRelatorio("vigenciaTed")}<select {...filterProps("vigenciaTed")} value={filters.vigenciaTed ?? ""}
        onChange={e => setFilters({ ...filters, vigenciaTed: e.target.value })}>
        <option value="">Todas</option>{opcoesDominio("situacaoVigencia").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select>{hint("vigenciaTed")}</label></div>
    <h2>Gerar relatório</h2>
    <p className="muted">Confira os filtros de cada relatório antes de escolher o formato.</p>
    <div className="report-actions">
    {CATALOGO_RELATORIOS.map(opcao => {
      const applied = filtrosDoRelatorio(opcao.codigo, filters);
      const ignored = Object.keys(filters).filter(name => filters[name] && !opcao.filtros.includes(name));
      const descriptionId = `report-${opcao.codigo}-filters`;
      return <div key={opcao.codigo} role="group" aria-labelledby={`report-${opcao.codigo}-name`}>
        <strong id={`report-${opcao.codigo}-name`}>{opcao.rotulo}</strong>{opcao.formatos.map(format =>
        <button key={format} disabled={generation.pending.has(generationKey(opcao.codigo, format))}
          aria-describedby={descriptionId}
          aria-busy={generation.pending.has(generationKey(opcao.codigo, format))}
          onClick={() => void generate(opcao.codigo, format)}>
          {generation.pending.has(generationKey(opcao.codigo, format)) ? `${format} · Gerando…` : format}</button>)}
        <p id={descriptionId} className="report-filter-summary muted">
          <span>{Object.keys(applied).length ? `Filtros aplicados: ${describeFilters(applied)}.` : "Será gerado sem filtros."}</span>
          {ignored.length > 0 && <span>Não se aplicam a este relatório: {ignored.map(rotuloFiltroRelatorio).join(", ")}.</span>}
        </p>
      </div>;
    })}</div>
    <div className="feedback-live" role="status" aria-atomic="true">
      {generation.pending.size > 0 && <p className="muted">Gerando: {[...generation.pending.values()].join("; ")}.</p>}
    </div>
    <h2>Histórico</h2>
    <ResourceState {...history} onRetry={history.reload} label="o histórico de relatórios" />
    {!history.loading && !history.error && !items.length && <p className="empty">Nenhum relatório gerado.</p>}
    {items.map(item => <article className="doc" key={item.id}><div className="report-metadata">
      <strong>{rotuloDominio(item.tipo)}</strong>
      <small>{item.nomeArquivo} · {item.formato} · {new Date(item.criadoEm).toLocaleString("pt-BR")}</small>
      <small>Gerado por {item.criadoPor.nome} ({item.criadoPor.login})</small>
      <small>Filtros: {Object.entries(item.filtros).length
        ? describeFilters(item.filtros)
        : "Sem filtros"}</small>
      <small className="checksum">SHA-256 · {item.checksumSha256 ?? "Não disponível"}</small>
      <small>Armazenamento · {item.chaveArmazenamento} · {item.tamanhoBytes} bytes</small></div>
      <button onClick={() => void download(`/api/v1/relatorios/${item.id}/arquivo`, token)
        .catch(error => notify(`${item.nomeArquivo}: ${(error as Error).message} Tente baixar novamente.`, "error"))}>Baixar</button></article>)}</section>;
}
