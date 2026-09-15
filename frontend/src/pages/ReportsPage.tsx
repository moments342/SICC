import { useCallback, useEffect, useState } from "react";
import { download, request } from "../api";
import { opcoesDominio, rotuloDominio } from "../domainLabels";
import { CATALOGO_RELATORIOS, filtrosDoRelatorio } from "../reportCatalog";
import type { FormatoRelatorio, TipoRelatorio } from "../domain";
import type { AuthenticatedPageProps, RelatorioGerado } from "../models";
export function Reports({ token, notify }: AuthenticatedPageProps) {
  const [items, setItems] = useState<RelatorioGerado[]>([]);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const load = useCallback(() => request<typeof items>("/api/v1/relatorios", {}, token).then(setItems), [token]);
  useEffect(() => { void load(); }, [load]);
  async function generate(type: TipoRelatorio, format: FormatoRelatorio) {
    try { await request("/api/v1/relatorios", { method: "POST", body: JSON.stringify({
      tipo: type, formato: format,
      filtros: filtrosDoRelatorio(type, filters)
    }) }, token); notify("Relatório gerado e retido para download."); await load(); }
    catch (e) { notify((e as Error).message); }
  }
  return <section className="panel"><h2>Filtros dos relatórios</h2>
    <div className="inline-form">{["numero", "origem", "ano", "dataInicial", "dataFinal"].map(name =>
      <label key={name}>{name}<input type={name.startsWith("data") ? "date" : "text"} value={filters[name] ?? ""}
        onChange={e => setFilters({ ...filters, [name]: e.target.value })} /></label>)}
      <label>Tipo<select value={filters.tipo ?? ""} onChange={e => setFilters({ ...filters, tipo: e.target.value })}>
        <option value="">Todos</option>{opcoesDominio("tipoInstrumento").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Contexto<select value={filters.contexto ?? ""}
        onChange={e => setFilters({ ...filters, contexto: e.target.value })}>
        <option value="">Todos</option>
        {opcoesDominio("contextoTramitacao").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}
      </select></label>
      <label>Status<select value={filters.status ?? ""} onChange={e => setFilters({ ...filters, status: e.target.value })}>
        <option value="">Todos</option>{opcoesDominio("statusProcesso").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Vigência contratual<select value={filters.vigenciaContratual ?? ""}
        onChange={e => setFilters({ ...filters, vigenciaContratual: e.target.value })}>
        <option value="">Todas</option>{opcoesDominio("situacaoVigencia").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Vigência TED<select value={filters.vigenciaTed ?? ""}
        onChange={e => setFilters({ ...filters, vigenciaTed: e.target.value })}>
        <option value="">Todas</option>{opcoesDominio("situacaoVigencia").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label></div>
    <h2>Gerar relatório</h2><div className="report-actions">
    {CATALOGO_RELATORIOS.map(opcao =>
      <div key={opcao.codigo}><strong>{opcao.rotulo}</strong>{opcao.formatos.map(format =>
        <button key={format} onClick={() => generate(opcao.codigo, format)}>{format}</button>)}</div>)}</div>
    <h2>Histórico</h2>{items.map(item => <article className="doc" key={item.id}><div className="report-metadata">
      <strong>{rotuloDominio(item.tipo)}</strong>
      <small>{item.nomeArquivo} · {item.formato} · {new Date(item.criadoEm).toLocaleString("pt-BR")}</small>
      <small>Gerado por {item.criadoPor.nome} ({item.criadoPor.login})</small>
      <small>Filtros: {Object.entries(item.filtros).length
        ? Object.entries(item.filtros).map(([nome, valor]) => `${nome}: ${rotuloDominio(valor)}`).join(" · ")
        : "Sem filtros"}</small>
      <small className="checksum">SHA-256 · {item.checksumSha256 ?? "Não disponível"}</small>
      <small>Armazenamento · {item.chaveArmazenamento} · {item.tamanhoBytes} bytes</small></div>
      <button onClick={() => download(`/api/v1/relatorios/${item.id}/arquivo`, token)}>Baixar</button></article>)}</section>;
}
