import { useEffect, useRef } from "react";
import { download, request } from "../api";
import { useResource } from "../useResource";
import { ResourceState } from "../components/Feedback";
import { useReportGeneration } from "../components/ReportGeneration";
import { FilterDisclosure } from "../components/FilterDisclosure";
import { AppliedFilters } from "../components/AppliedFilters";
import { useRouteState } from "../useRouteState";
import { opcoesDominio, rotuloDominio } from "../domainLabels";
import { CATALOGO_RELATORIOS, filtrosDoRelatorio, rotuloFiltroRelatorio } from "../reportCatalog";
import type { FormatoRelatorio, TipoRelatorio } from "../domain";
import type { AuthenticatedPageProps, FiltrosRelatorio, RelatorioGerado } from "../models";

function filterHint(name: string) {
  return ({ ano: "Ano da atividade: cadastros, formalizações, conclusões e permanências. Em branco, usa o ano atual. A carteira é apresentada na data de emissão.",
    setorId: "Destino da última movimentação da tramitação de formalização. Considera também setores inativos ainda vinculados a processos.",
    responsavelId: "Pessoa da DIPAC atualmente atribuída ao processo. Inclui responsáveis inativos ainda vinculados.",
    dataInicial: "Primeiro dia incluído no período da tramitação.", dataFinal: "Último dia incluído no período da tramitação." } as Record<string, string>)[name];
}

export function Reports({ token, notify }: AuthenticatedPageProps) {
  const filterKeys = ["numero", "origem", "ano", "dataInicial", "dataFinal", "tipo", "contexto", "status", "vigenciaContratual", "vigenciaTed", "setorId", "responsavelId"];
  const [route, updateRoute] = useRouteState("relatorios", ["relatorio", ...filterKeys]);
  const reportType = CATALOGO_RELATORIOS.find(item => item.codigo === route.relatorio)?.codigo ?? "ANUAL_PROCESSOS";
  const setReportType = (value: TipoRelatorio) => updateRoute({ relatorio: value });
  const selectedReport = CATALOGO_RELATORIOS.find(item => item.codigo === reportType)!;
  const generation = useReportGeneration();
  const notifiedRevision = useRef(generation.revision);
  useEffect(() => {
    if (notifiedRevision.current === generation.revision) return;
    notifiedRevision.current = generation.revision;
    notify(generation.error || "Relatório gerado e retido para download.", generation.error ? "error" : "success");
  }, [generation.revision, generation.error, notify]);
  const history = useResource<RelatorioGerado[]>("/api/v1/relatorios", token, generation.revision);
  const catalog = useResource<FiltrosRelatorio>("/api/v1/relatorios/filtros", token, generation.revision);
  const setores = catalog.data?.setores ?? [];
  const responsaveis = catalog.data?.responsaveis ?? [];
  function describeFilter(name: string, value: string) {
    if (name === "setorId") return value === "SEM_SETOR" ? "Sem setor atual"
      : setores.find(s => String(s.id) === value)?.rotulo ?? `Setor #${value}`;
    if (name === "responsavelId") return value === "SEM_RESPONSAVEL" ? "Sem responsável"
      : responsaveis.find(r => String(r.id) === value)?.rotulo ?? `Responsável #${value}`;
    return rotuloDominio(value);
  }
  function describeFilters(values: Record<string, string>) {
    return Object.entries(values).map(([name, value]) => `${rotuloFiltroRelatorio(name)}: ${describeFilter(name, value)}`).join(" · ");
  }
  const items = history.data ?? [];
  const { relatorio, ...filters } = route;
  const setFilters = (value: Record<string, string>) => updateRoute({ ...Object.fromEntries(filterKeys.map(key => [key, ""])), ...value }, true);
  const filterProps = (name: string) => ({
    "aria-label": rotuloFiltroRelatorio(name), "aria-describedby": filterHint(name) ? `report-filter-${name}-hint` : undefined
  });
  const hint = (name: string) => filterHint(name) && <small className="muted" id={`report-filter-${name}-hint`}>{filterHint(name)}</small>;
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
  return <section className="panel reports-panel"><h2>Gerar relatório</h2>
    <label>Tipo de relatório<select aria-label="Tipo de relatório" value={reportType}
      onChange={e => setReportType(e.target.value as TipoRelatorio)}>
      {CATALOGO_RELATORIOS.map(item => <option key={item.codigo} value={item.codigo}>{item.rotulo}</option>)}
    </select></label>
    {reportType === "ANUAL_PROCESSOS" && <p className="muted">Atividade no ano escolhido, incluindo processos de anos anteriores, acompanhada dos indicadores atuais da carteira filtrada.</p>}
    {reportType === "CONSOLIDADO" && <p className="muted">Indicadores atuais da carteira filtrada, atividade mensal, permanência por setor e fichas dos processos.</p>}
    <FilterDisclosure label="Filtros do relatório" count={Object.keys(filtrosDoRelatorio(reportType, filters)).length}>
    {["numero", "origem", "ano", "dataInicial", "dataFinal"].filter(name => selectedReport.filtros.includes(name)).map(name =>
      <label key={name}>{rotuloFiltroRelatorio(name)}<input {...filterProps(name)} type={name.startsWith("data") ? "date" : name === "ano" ? "number" : "text"} min={name === "ano" ? 1 : undefined} max={name === "ano" ? 9999 : undefined} value={filters[name] ?? ""}
        onChange={e => setFilters({ ...filters, [name]: e.target.value })} />{hint(name)}</label>)}
      <ResourceState {...catalog} onRetry={catalog.reload} label="os setores e responsáveis dos relatórios" />
      <label>{rotuloFiltroRelatorio("setorId")}<select {...filterProps("setorId")} value={filters.setorId ?? ""}
        disabled={catalog.loading || Boolean(catalog.error)} onChange={e => setFilters({ ...filters, setorId: e.target.value })}>
        <option value="">Todos</option><option value="SEM_SETOR">Sem setor atual</option>
        {filters.setorId && filters.setorId !== "SEM_SETOR" && !setores.some(s => String(s.id) === filters.setorId)
          && <option value={filters.setorId}>Setor #{filters.setorId}</option>}
        {setores.map(s => <option key={s.id} value={s.id}>{s.rotulo}</option>)}
      </select>{hint("setorId")}</label>
      <label>{rotuloFiltroRelatorio("responsavelId")}<select {...filterProps("responsavelId")} value={filters.responsavelId ?? ""}
        disabled={catalog.loading || Boolean(catalog.error)} onChange={e => setFilters({ ...filters, responsavelId: e.target.value })}>
        <option value="">Todos</option><option value="SEM_RESPONSAVEL">Sem responsável</option>
        {filters.responsavelId && filters.responsavelId !== "SEM_RESPONSAVEL" && !responsaveis.some(r => String(r.id) === filters.responsavelId)
          && <option value={filters.responsavelId}>Responsável #{filters.responsavelId}</option>}
        {responsaveis.map(r => <option key={r.id} value={r.id}>{r.rotulo}</option>)}
      </select>{hint("responsavelId")}</label>
      <label>{rotuloFiltroRelatorio("tipo")}<select {...filterProps("tipo")} value={filters.tipo ?? ""} onChange={e => setFilters({ ...filters, tipo: e.target.value })}>
        <option value="">Todos</option>{opcoesDominio("tipoInstrumento").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select>{hint("tipo")}</label>
      {selectedReport.filtros.includes("contexto") && <label>{rotuloFiltroRelatorio("contexto")}<select {...filterProps("contexto")} value={filters.contexto ?? ""}
        onChange={e => setFilters({ ...filters, contexto: e.target.value })}>
        <option value="">Todos</option>
        {opcoesDominio("contextoTramitacao").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}
      </select>{hint("contexto")}</label>}
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
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select>{hint("vigenciaTed")}</label>
      {Object.values(filters).some(Boolean) && <button type="button" className="secondary-action" onClick={() => setFilters({})}>Limpar filtros</button>}
    </FilterDisclosure>
    <AppliedFilters values={filtrosDoRelatorio(reportType, filters)} describe={describeFilter} onRemove={key => setFilters({ ...filters, [key]: "" })} />
    <h3>Formato do arquivo</h3>
    <p className="muted">Escolha o formato para gerar o arquivo. Ele ficará disponível em Relatórios gerados.</p>
    <div className="report-actions">
    {CATALOGO_RELATORIOS.filter(item => item.codigo === reportType).map(opcao => {
      const applied = filtrosDoRelatorio(opcao.codigo, filters);
      const ignored = Object.keys(filters).filter(name => filters[name] && !opcao.filtros.includes(name));
      const descriptionId = `report-${opcao.codigo}-filters`;
      return <div key={opcao.codigo} role="group" aria-labelledby={`report-${opcao.codigo}-name`}>
        <strong id={`report-${opcao.codigo}-name`}>{opcao.rotulo}</strong>{opcao.formatos.map(format =>
        <button key={format} disabled={generation.pending.has(generationKey(opcao.codigo, format))}
          aria-describedby={descriptionId}
          aria-busy={generation.pending.has(generationKey(opcao.codigo, format))}
          onClick={() => void generate(opcao.codigo, format)}>
          {generation.pending.has(generationKey(opcao.codigo, format)) ? `${format} · Gerando…` : `Gerar ${format}`}</button>)}
        <p id={descriptionId} className="report-filter-summary muted">
          <span>{Object.keys(applied).length ? `Filtros aplicados: ${describeFilters(applied)}.` : "Será gerado sem filtros."}</span>
          {ignored.length > 0 && <span>Não se aplicam a este relatório: {ignored.map(rotuloFiltroRelatorio).join(", ")}.</span>}
        </p>
      </div>;
    })}</div>
    <div className="feedback-live" role="status" aria-atomic="true">
      {generation.pending.size > 0 && <p className="muted">Gerando: {[...generation.pending.values()].join("; ")}.</p>}
    </div>
    <h2>Relatórios gerados</h2>
    <ResourceState {...history} onRetry={history.reload} label="o histórico de relatórios" />
    {!history.loading && !history.error && !items.length && <p className="empty">Nenhum relatório gerado.</p>}
    {items.map(item => <article className="doc" key={item.id}><div className="report-metadata">
      <strong id={`report-label-${item.id}`}>{rotuloDominio(item.tipo)}</strong>
      <small>{item.nomeArquivo} · {item.formato} · {new Date(item.criadoEm).toLocaleString("pt-BR")}</small>
      <small>Gerado por {item.criadoPor.nome} ({item.criadoPor.login})</small>
      <small>Filtros: {Object.entries(item.filtros).length
        ? describeFilters(item.filtros)
        : "Sem filtros"}</small>
      <details><summary>Detalhes técnicos</summary><small className="checksum">SHA-256 · {item.checksumSha256 ?? "Não disponível"}</small>
      <small>Armazenamento · {item.chaveArmazenamento} · {item.tamanhoBytes} bytes</small></details></div>
      <button className="secondary-action" aria-describedby={`report-label-${item.id}`} onClick={() => void download(`/api/v1/relatorios/${item.id}/arquivo`, token)
        .catch(error => notify(`${item.nomeArquivo}: ${(error as Error).message} Tente baixar novamente.`, "error"))}>Baixar</button></article>)}</section>;
}
