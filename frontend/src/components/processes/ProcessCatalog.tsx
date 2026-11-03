import { MutationForm } from "../MutationForm";
import type { Notify } from "../../models";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { FilterDisclosure } from "../FilterDisclosure";
import { AppliedFilters } from "../AppliedFilters";
import { useRouteState } from "../../useRouteState";
import { formatarDataNegocio } from "../../formatters";
import { ApiError, request } from "../../api";
import { useResource } from "../../useResource";
import { ResourceState } from "../Feedback";
import { responsavelSelecionado } from "../../formValues";
import { opcoesDominio, rotuloDominio } from "../../domainLabels";
import type { Page, ProcessoAdministrativo, ResponsavelProcesso } from "../../models";
import { Badge } from "../Presentation";

const emptyPage: Page<ProcessoAdministrativo> = {
  content: [], totalElements: 0, totalPages: 0, number: 0, size: 20
};

type CommonProps = { token: string; notify: Notify };

export function ProcessCreationPanel({ token, notify, responsaveis, onCreated, embedded = false }: CommonProps & {
  responsaveis: ResponsavelProcesso[];
  onCreated: () => void;
  embedded?: boolean;
}) {
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const fieldProps = (field: string) => ({
    "aria-invalid": fields[field] ? true : undefined,
    "aria-describedby": fields[field] ? `create-${field}-error` : undefined
  });
  const fieldError = (field: string) => fields[field] &&
    <span className="field-error" id={`create-${field}-error`}>{fields[field]}</span>;
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setError(""); setFields({});
    try {
      await request("/api/v1/processos", { method: "POST", body: JSON.stringify({
        numero: data.get("numero"), origem: data.get("origem"), numeroProjeto: data.get("projeto"),
        responsavelId: responsavelSelecionado(data)
      }) }, token);
      form.reset();
      notify("Processo Administrativo criado.");
      onCreated();
    } catch (cause) {
      const validation = cause instanceof ApiError ? cause.fields : {};
      setFields(validation);
      setError(Object.keys(validation).length ? "Revise os campos indicados e tente cadastrar novamente." : (cause as Error).message);
      const names: Record<string, string> = { numero: "numero", origem: "origem", numeroProjeto: "projeto", responsavelId: "responsavel" };
      const first = Object.keys(names).find(field => validation[field]);
      const control = first ? form.elements.namedItem(names[first]) : null;
      if (control instanceof HTMLElement) requestAnimationFrame(() => control.focus());
      return false;
    }
  }

  return <section className={embedded ? "modal-form" : "panel"}>{!embedded && <h2>Novo Processo Administrativo</h2>}
    <MutationForm formKey="process-create" className="stack" onSubmit={create}>
      <div className="feedback-live" role="alert" aria-atomic="true">{error && <p className="error">{error}</p>}</div>
      <p className="muted form-hint">Número e origem são obrigatórios. Os demais campos são opcionais.</p>
      <label>Número<input name="numero" placeholder="Ex.: 23005.011488/2026-01" maxLength={60} {...fieldProps("numero")} required />{fieldError("numero")}</label>
      <label>Origem<input name="origem" placeholder="Unidade ou instituição de origem" maxLength={150} {...fieldProps("origem")} required />{fieldError("origem")}</label>
      <label>Número do projeto<input name="projeto" maxLength={80} {...fieldProps("numeroProjeto")} />{fieldError("numeroProjeto")}</label>
      <label>Responsável DIPAC<select name="responsavel" {...fieldProps("responsavelId")}><option value="">Sem responsável</option>
        {responsaveis.map(responsavel => <option key={responsavel.id} value={responsavel.id}>
          {responsavel.nome} · {rotuloDominio(responsavel.perfil)}
        </option>)}</select>{fieldError("responsavelId")}</label>
      <button className="primary">Cadastrar processo</button></MutationForm></section>;
}

export type ProcessFilterRequest = { filters: Record<string, string> };

export function ProcessCatalogPanel({ token, selectedId, refreshKey, onSelect, onCreate, creating, filterRequest, responsaveis }: CommonProps & {
  selectedId?: number;
  refreshKey: number;
  onSelect: (processo: ProcessoAdministrativo) => void;
  onCreate: () => void; creating: boolean; filterRequest?: ProcessFilterRequest | null;
  responsaveis: ResponsavelProcesso[];
}) {
  const keys = ["numero", "origem", "objeto", "coordenador", "tipo", "status", "vigencia", "vigenciaContratual", "vigenciaTed", "responsavelId"];
  const [route, updateRoute] = useRouteState("processos", [...keys, "pagina", "processo", "ordenacao"]);
  const filters = useMemo(() => Object.fromEntries(Object.entries(route).filter(([key]) => keys.includes(key))), [route]);
  const [draftFilters, setDraftFilters] = useState<Record<string, string>>(filters);
  const pageNumber = Math.max(0, Number.parseInt(route.pagina, 10) || 0);
  const setPageNumber = (value: number) => updateRoute({ pagina: value ? String(value) : "" });
  function applyFilters(values: Record<string, string>) {
    updateRoute({ ...Object.fromEntries(keys.map(key => [key, ""])), ...values, pagina: "", processo: "" });
  }
  useEffect(() => { setDraftFilters(filters); }, [filters]);
  const filterForm = useRef<HTMLFormElement>(null);
  const hasFilters = Object.keys(filters).length > 0;
  const query = useMemo(() => {
    const { responsavelId, ...criteria } = filters;
    return new URLSearchParams({ page: String(pageNumber), size: "20", ...criteria,
      ...(route.ordenacao ? { ordenacao: route.ordenacao } : {}),
      ...(responsavelId === "SEM_RESPONSAVEL" ? { semResponsavel: "true" } : responsavelId ? { responsavelId } : {}) }).toString();
  }, [filters, pageNumber, route.ordenacao]);
  const catalog = useResource<Page<ProcessoAdministrativo>>(`/api/v1/processos?${query}`, token, refreshKey);
  const page = catalog.data ?? emptyPage;
  useEffect(() => {
    if (!filterRequest) return;
    applyFilters(filterRequest.filters); setDraftFilters(filterRequest.filters);
  }, [filterRequest]);
  const control = (name: string) => ({ name, value: draftFilters[name] ?? "",
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
      setDraftFilters(current => ({ ...current, [name]: event.target.value })) });

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    applyFilters(Object.fromEntries([...new FormData(event.currentTarget).entries()]
      .filter(([, value]) => String(value).trim()).map(([key, value]) => [key, String(value)])));
  }

  function clearFilters() {
    setDraftFilters({});
    applyFilters({});
    filterForm.current?.querySelector<HTMLInputElement>('input[name="numero"]')?.focus();
  }

  return <section className="panel span process-catalog"><div className="panel-title"><h2 aria-label="Processos Administrativos ativos">Processos ativos</h2>
    <button className="secondary-action" aria-label="Novo Processo Administrativo" aria-haspopup="dialog" aria-expanded={creating} onClick={onCreate}>Novo processo</button></div>
    <form ref={filterForm} className="compact-filters" onSubmit={filter}>
      <div className="inline-form filter-search"><label>Filtrar por número<input {...control("numero")} type="search" placeholder="Número do Processo Administrativo" /></label>
      <button className="primary">Filtrar</button>
      {hasFilters && <button type="button" className="secondary-action" onClick={clearFilters}>Limpar filtros</button>}</div>
      <FilterDisclosure count={Object.keys(filters).filter(name => name !== "numero").length}>
      <label>Filtrar por origem<input {...control("origem")} /></label><label>Filtrar por objeto<input {...control("objeto")} /></label>
      <label>Filtrar por coordenador<input {...control("coordenador")} /></label>
      <label>Filtrar por tipo<select {...control("tipo")}><option value="">Todos</option>{opcoesDominio("tipoInstrumento").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Filtrar por status<select {...control("status")}><option value="">Todos</option>{opcoesDominio("statusProcesso").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Filtrar por vigência (contratual ou TED)<select {...control("vigencia")}><option value="">Todas</option>{opcoesDominio("situacaoVigencia").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      {[['vigenciaContratual', 'Vigência contratual'], ['vigenciaTed', 'Vigência do TED']].map(([name, label]) =>
        <label key={name}>{label}<select {...control(name)} aria-label={label}><option value="">Todas</option>{opcoesDominio("situacaoVigencia").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>)}
      <label>Filtrar por responsável DIPAC<select {...control("responsavelId")}><option value="">Todos</option>
        <option value="SEM_RESPONSAVEL">Não atribuído</option>{responsaveis.map(item => <option key={item.id} value={item.id}>{item.nome}</option>)}
      </select></label>
      <p className="muted filter-explanation">Os filtros são combinados. “Contratual ou TED” aceita qualquer uma das duas vigências; os filtros individuais exigem a situação de cada vigência escolhida.</p>
      <button className="secondary-action">Aplicar filtros</button></FilterDisclosure></form>
    <AppliedFilters values={filters} describe={(key, value) => key === "responsavelId"
      ? value === "SEM_RESPONSAVEL" ? "Não atribuído" : responsaveis.find(item => String(item.id) === value)?.nome ?? "Usuário selecionado"
      : rotuloDominio(value)} onRemove={key => applyFilters({ ...filters, [key]: "" })} />
    <div className="catalog-toolbar">
      {!catalog.loading && !catalog.error && <p className="result-count" role="status">{page.totalElements} {page.totalElements === 1 ? "Processo Administrativo" : "Processos Administrativos"} {hasFilters ? (page.totalElements === 1 ? "encontrado" : "encontrados") : (page.totalElements === 1 ? "ativo" : "ativos")}</p>}
      <label><span id="process-order-label" className="catalog-order-label">Ordenar por</span><select aria-labelledby="process-order-label" value={route.ordenacao ?? ""} onChange={event => updateRoute({ ordenacao: event.target.value, pagina: "", processo: "" })}>
        <option value="">Ordem de cadastro</option><option value="VIGENCIA_CONTRATUAL">Vencimento contratual</option><option value="VIGENCIA_TED">Vencimento do TED</option>
      </select></label>
    </div>
    <ResourceState {...catalog} onRetry={catalog.reload} label="os processos administrativos" />
    <div className="cards">{page.content.map(processo => <button key={processo.id}
      aria-pressed={selectedId === processo.id}
      className={`process-card ${selectedId === processo.id ? "selected" : ""}`} onClick={() => onSelect(processo)}>
      <div><strong>{processo.numero}</strong><small>{processo.origem} · {processo.numeroProjeto ?? "sem projeto"}</small>
        {processo.instrumento && <small>{rotuloDominio(processo.instrumento.tipo)} · {processo.instrumento.numero}</small>}</div>
      <Badge value={processo.status} /><small>Responsável: {processo.responsavel?.nome ?? "não atribuído"}</small>
      <small>Setor atual: {processo.setorAtual ?? "sem movimentação"}</small>
      <span className="process-dates">{processo.instrumento ? <>
        <span><small>Vigência contratual</small><strong>{processo.instrumento.vigenciaContratualFinal ? formatarDataNegocio(processo.instrumento.vigenciaContratualFinal) : "Não informada"}</strong>
          <small>{rotuloDominio(processo.instrumento.situacaoContratual ?? "")}</small></span>
        <span><small>Vigência do TED</small><strong>{processo.instrumento.vigenciaTedFinal ? formatarDataNegocio(processo.instrumento.vigenciaTedFinal) : "Não informada"}</strong>
          <small>{rotuloDominio(processo.instrumento.situacaoTed ?? "")}</small></span>
      </> : <small>Ainda não formalizado</small>}</span></button>)}</div>
    <div className="feedback-live" role="status" aria-atomic="true">
      {!catalog.loading && !catalog.error && !page.content.length && <p className="empty">
        {hasFilters ? "Nenhum Processo Administrativo corresponde aos filtros. Revise os filtros ou use Limpar filtros para ver os processos ativos."
          : page.totalElements === 0 ? "Nenhum Processo Administrativo ativo cadastrado. Use Novo Processo Administrativo para cadastrar um registro."
          : "Nenhum Processo Administrativo nesta página. Volte à página anterior."}
      </p>}
    </div>
    {page.totalPages > 1 && <div className="pagination"><button disabled={page.number === 0}
      onClick={() => setPageNumber(page.number - 1)}>Página anterior</button><span>Página {page.number + 1} de {page.totalPages}</span>
      <button disabled={page.number + 1 >= page.totalPages}
        onClick={() => setPageNumber(page.number + 1)}>Próxima página</button></div>}
  </section>;
}
