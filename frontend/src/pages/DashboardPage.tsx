import { FormEvent, useEffect, useRef, useState } from "react";
import { useResource } from "../useResource";
import { useRouteState } from "../useRouteState";
import { AppliedFilters, resetFilterField } from "../components/AppliedFilters";
import { formatarDataNegocio, formatarMes, formatarNumeroDias, money } from "../formatters";
import { opcoesDominio } from "../domainLabels";
import { Metric } from "../components/Presentation";
import { Modal } from "../components/Modal";
import { FilterDisclosure } from "../components/FilterDisclosure";
import type { DashboardData } from "../models";

type Detalhe = { tipo: "setor"; setor: string } | { tipo: "tempoInicial" };
const DETAIL_PAGE_SIZE = 20;

export function Dashboard({ token, onProcesses }: { token: string; onProcesses: (filters: Record<string, string>) => void }) {
  const [detalhe, setDetalhe] = useState<Detalhe | null>(null);
  const [detailSearch, setDetailSearch] = useState("");
  const [detailPage, setDetailPage] = useState(0);
  const paginationFocus = useRef<HTMLButtonElement | null>(null);
  function openDetail(value: Detalhe) {
    setDetailSearch(""); setDetailPage(0); setDetalhe(value);
  }
  const [filters, updateFilters] = useRouteState("dashboard", ["origem", "tipo", "status"]);
  function setFilters(next: Record<string, string>) { updateFilters({ origem: "", tipo: "", status: "", ...next }); }
  const filterForm = useRef<HTMLFormElement>(null);
  const query = new URLSearchParams(filters).toString();
  const resource = useResource<DashboardData>(`/api/v1/dashboard${query ? `?${query}` : ""}`, token);
  const { data, loading, error, reload: load } = resource;
  useEffect(() => {
    setDetalhe(null);
    for (const key of ["origem", "tipo", "status"]) {
      const field = filterForm.current?.elements.namedItem(key);
      if (field instanceof HTMLInputElement || field instanceof HTMLSelectElement) field.value = filters[key] ?? "";
    }
  }, [query]);

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFilters(Object.fromEntries([...new FormData(event.currentTarget).entries()]
      .filter(([, value]) => String(value).trim())
      .map(([key, value]) => [key, String(value).trim()])));
  }

  const total = data ? Object.values(data.processosPorStatus).reduce((sum, value) => sum + value, 0) : 0;
  const meses = data ? [...new Set([
    ...Object.keys(data.formalizacoesMensais), ...Object.keys(data.conclusoesMensais)
  ])].sort() : [];
  const permanenciasDetalhadas = data && detalhe?.tipo === "setor"
    ? data.detalhesPermanenciaPorSetor?.[detalhe.setor] ?? []
    : [];
  const matches = (item: { numeroProcesso: string }) => item.numeroProcesso.toLocaleLowerCase("pt-BR")
    .includes(detailSearch.trim().toLocaleLowerCase("pt-BR"));
  const permanenciasFiltradas = permanenciasDetalhadas.filter(matches);
  const temposFiltrados = (data?.detalhesTempoTramitacaoInicial ?? []).filter(matches);
  const detailCount = detalhe?.tipo === "setor" ? permanenciasFiltradas.length : temposFiltrados.length;
  const pageCount = Math.ceil(detailCount / DETAIL_PAGE_SIZE);
  const currentPage = Math.min(detailPage, Math.max(0, pageCount - 1));
  const start = currentPage * DETAIL_PAGE_SIZE;
  useEffect(() => {
    const trigger = paginationFocus.current;
    paginationFocus.current = null;
    if (!trigger?.closest('dialog[open]')) return;
    const target = trigger.disabled
      ? trigger.parentElement?.querySelector<HTMLButtonElement>('button:not(:disabled)')
      : trigger;
    target?.focus({ preventScroll: true });
  }, [currentPage]);

  return <div className="dashboard-stack"><form ref={filterForm} className="panel dashboard-filters" onSubmit={filter}>
    <FilterDisclosure label="Filtros do painel" count={Object.keys(filters).length}>
    <label>Origem do processo<input name="origem" /></label>
    <label>Tipo do instrumento<select name="tipo"><option value="">Todos</option>
      {opcoesDominio("tipoInstrumento").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
    <label>Status do processo<select name="status"><option value="">Todos</option>
      {opcoesDominio("statusProcesso").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
    <button className="primary" disabled={loading}>Aplicar filtros</button>
    {Object.keys(filters).length > 0 && <button type="button" className="secondary-action" onClick={() => { filterForm.current?.reset(); setFilters({}); }}>Limpar filtros</button>}
    </FilterDisclosure>
  </form>
  <AppliedFilters values={filters} onRemove={key => { resetFilterField(filterForm.current, key); updateFilters({ [key]: "" }); }} />
  <div className="feedback-live" role="status" aria-atomic="true">{loading && <section className="dashboard-state panel">Carregando painel…</section>}</div>
  <div className="feedback-live" role="alert" aria-atomic="true">{!loading && error && <section className="dashboard-state panel error-state"><p>{error}</p>
    <button onClick={() => void load()}>Tentar novamente</button></section>}</div>
  {!loading && !error && data && total === 0 && <section className="dashboard-state panel" role="status">
    Nenhum Processo Administrativo corresponde aos filtros.</section>}
  {!loading && !error && data && total > 0 && <><section className="panel dashboard-priorities" aria-label="Vigências que exigem atenção">
    <div className="priority-heading"><h2>Vigências</h2><p className="muted">Vencimentos nos próximos 120 dias</p></div>
    <div className="expiry-alerts">
      <div><div className="expiry-count"><span>Vigência contratual</span><strong>{data.alertasContratuais}</strong></div>
        <button className="secondary-action" disabled={!data.alertasContratuais} onClick={() => onProcesses({ ...filters, vigenciaContratual: "PROXIMA_VENCIMENTO" })}>Ver processos · vigência contratual</button></div>
      <div><div className="expiry-count"><span>Vigência do TED</span><strong>{data.alertasTed}</strong></div>
        <button className="secondary-action" disabled={!data.alertasTed} onClick={() => onProcesses({ ...filters, vigenciaTed: "PROXIMA_VENCIMENTO" })}>Ver processos · vigência do TED</button></div>
    </div>
    {!data.alertasContratuais && !data.alertasTed && <p className="muted expiry-empty">Nenhuma vigência vence nesse período.</p>}
  </section><div className="metrics">
    <Metric label="Em formalização" value={data.processosPorStatus.EM_FORMALIZACAO ?? 0} />
    <Metric label="Em vigência" value={data.processosPorStatus.EM_VIGENCIA ?? 0} />
    <Metric label="Concluídos" value={data.processosPorStatus.CONCLUIDO ?? 0} />
    <Metric label="Percentual concluído" value={`${data.percentualConcluidos.toFixed(1)}%`} />
  </div><div className="grid two dashboard-details"><section className="panel"><h2>Tramitação</h2>
    <button type="button" className="alert-row dashboard-indicator"
      disabled={!data.maiorGargalo}
      aria-label={`Maior gargalo · ${data.maiorGargalo ?? "Sem dados"}`}
      aria-expanded={detalhe?.tipo === "setor" && detalhe.setor === data.maiorGargalo}
      aria-haspopup="dialog"
      onClick={() => { if (data.maiorGargalo) openDetail({ tipo: "setor", setor: data.maiorGargalo }); }}>
      <span>Maior gargalo</span><strong>{data.maiorGargalo ?? "Sem dados"}</strong></button>
    <button type="button" className="alert-row dashboard-indicator"
      aria-label={`Tempo inicial médio · ${formatarNumeroDias(data.tempoMedioTramitacaoInicialDias)} dias`}
      aria-expanded={detalhe?.tipo === "tempoInicial"}
      aria-haspopup="dialog" onClick={() => openDetail({ tipo: "tempoInicial" })}>
      <span>Tempo inicial médio</span><strong>{formatarNumeroDias(data.tempoMedioTramitacaoInicialDias)} dias</strong></button>
    {Object.entries(data.permanenciaMediaPorSetor).map(([name, days]) =>
      <button type="button" className="bar dashboard-indicator" key={name}
        aria-label={`${name} · média de ${formatarNumeroDias(days)} dias`}
        aria-expanded={detalhe?.tipo === "setor" && detalhe.setor === name}
        aria-haspopup="dialog" onClick={() => openDetail({ tipo: "setor", setor: name })}>
        <span>{name}</span><i style={{ width: `${(days / Math.max(1, ...Object.values(data.permanenciaMediaPorSetor))) * 100}%` }} />
        <b>{formatarNumeroDias(days)}d</b></button>)}
  </section>
    <section className="panel"><h2>Instrumentos por tipo</h2>
      {opcoesDominio("tipoInstrumento").map(opcao =>
        <div className="alert-row" key={opcao.codigo}><span>{opcao.rotulo}</span>
          <strong>{data.instrumentosPorTipo[opcao.codigo] ?? 0}</strong></div>)}
    </section>
    <section className="panel dashboard-monthly"><div className="panel-title"><h2>Atividade mensal</h2>
      <p className="dashboard-value"><span>Valor total vigente</span><strong>{money(data.valorTotalVigente)}</strong></p></div>
      <div className="table-wrap"><table><thead><tr><th>Mês</th><th className="numeric">Formalizações</th><th className="numeric">Conclusões</th></tr></thead>
        <tbody>{meses.map(mes => <tr key={mes}><td>{formatarMes(mes)}</td>
          <td className="numeric">{data.formalizacoesMensais[mes] ?? 0}</td><td className="numeric">{data.conclusoesMensais[mes] ?? 0}</td></tr>)}</tbody>
      </table></div>
    </section>
  </div>
  </>}
  <Modal open={detalhe !== null} wide
    title={detalhe?.tipo === "setor" ? `Permanências em ${detalhe.setor}` : "Tempo de Tramitação Inicial"}
    description={detalhe?.tipo === "setor"
      ? "Períodos usados na média exibida, incluindo permanências ainda abertas."
      : "Todos os processos participam da média, inclusive os ainda não formalizados."}
    closeLabel="Fechar detalhamento" onClose={() => setDetalhe(null)}
    footer={detailCount > DETAIL_PAGE_SIZE && <div className="pagination" role="group" aria-label="Paginação do detalhamento">
      <button type="button" className="secondary-action" disabled={currentPage === 0}
        onClick={event => { paginationFocus.current = event.currentTarget; setDetailPage(currentPage - 1); }}>Anterior</button>
      <span>Página {currentPage + 1} de {pageCount}</span>
      <button type="button" className="secondary-action" disabled={currentPage + 1 >= pageCount}
        onClick={event => { paginationFocus.current = event.currentTarget; setDetailPage(currentPage + 1); }}>Próxima</button>
    </div>}>
    <section className="dashboard-metric-details stack">
      <label>Buscar por número do processo<input type="search" value={detailSearch}
        onChange={event => { setDetailSearch(event.target.value); setDetailPage(0); }} /></label>
      <p className="muted detail-count" role="status">{detailCount
        ? `${start + 1}–${Math.min(start + DETAIL_PAGE_SIZE, detailCount)} de ${detailCount} registro(s)`
        : "Nenhum registro corresponde à busca."}</p>
    {detalhe?.tipo === "setor" && <div className="table-wrap detail-table"><table><thead><tr><th>Processo Administrativo</th><th>Período</th>
      <th>Permanência</th><th>Situação</th></tr></thead><tbody>
      {permanenciasFiltradas.slice(start, start + DETAIL_PAGE_SIZE).map((item, indice) => <tr
        key={`${item.processoId}-${item.dataChegada}-${indice}`}><td data-label="Processo Administrativo"><strong>{item.numeroProcesso}</strong></td>
        <td data-label="Período">{formatarDataNegocio(item.dataChegada)} a {item.aberta
          ? "hoje" : formatarDataNegocio(item.dataSaida!)}</td>
        <td data-label="Permanência">{item.diasCorridos} dias</td><td data-label="Situação">{item.aberta ? "Aberta" : "Encerrada"}</td></tr>)}
    </tbody></table></div>}
    {detalhe?.tipo === "tempoInicial" && <div className="table-wrap detail-table"><table><thead><tr><th>Processo Administrativo</th><th>Cadastro</th>
      <th>Formalização</th><th>Tempo inicial</th></tr></thead><tbody>
      {temposFiltrados.slice(start, start + DETAIL_PAGE_SIZE).map(item => <tr key={item.processoId}>
        <td data-label="Processo Administrativo"><strong>{item.numeroProcesso}</strong></td><td data-label="Cadastro">{formatarDataNegocio(item.dataCadastro)}</td>
        <td data-label="Formalização">{item.aberta ? "Ainda não formalizado" : formatarDataNegocio(item.dataFormalizacao!)}</td>
        <td data-label="Tempo inicial">{item.aberta ? "Aberto" : "Formalizado"} · {item.diasCorridos} dias</td></tr>)}
    </tbody></table></div>}
    </section>
  </Modal></div>;
}
