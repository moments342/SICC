import type { Notify } from "../../models";
import { FormEvent, useMemo, useRef, useState } from "react";
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

export function ProcessCreationPanel({ token, notify, responsaveis, onCreated }: CommonProps & {
  responsaveis: ResponsavelProcesso[];
  onCreated: () => void;
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
      if (control instanceof HTMLElement) control.focus();
    }
  }

  return <section className="panel"><h2>Novo Processo Administrativo</h2>
    <form className="stack" onSubmit={create}>
      <div className="feedback-live" role="alert" aria-atomic="true">{error && <p className="error">{error}</p>}</div>
      <label>Número<input name="numero" maxLength={60} {...fieldProps("numero")} required />{fieldError("numero")}</label>
      <label>Origem<input name="origem" maxLength={150} {...fieldProps("origem")} required />{fieldError("origem")}</label>
      <label>Número do projeto<input name="projeto" maxLength={80} {...fieldProps("numeroProjeto")} />{fieldError("numeroProjeto")}</label>
      <label>Responsável DIPAC<select name="responsavel" {...fieldProps("responsavelId")}><option value="">Sem responsável</option>
        {responsaveis.map(responsavel => <option key={responsavel.id} value={responsavel.id}>
          {responsavel.nome} · {rotuloDominio(responsavel.perfil)}
        </option>)}</select>{fieldError("responsavelId")}</label>
      <button className="primary">Cadastrar processo</button></form></section>;
}

export function ProcessCatalogPanel({ token, selectedId, refreshKey, onSelect }: CommonProps & {
  selectedId?: number;
  refreshKey: number;
  onSelect: (processo: ProcessoAdministrativo) => void;
}) {
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [pageNumber, setPageNumber] = useState(0);
  const filterForm = useRef<HTMLFormElement>(null);
  const hasFilters = Object.keys(filters).length > 0;
  const query = useMemo(() => new URLSearchParams({ page: String(pageNumber), size: "20", ...filters }).toString(),
    [filters, pageNumber]);
  const catalog = useResource<Page<ProcessoAdministrativo>>(`/api/v1/processos?${query}`, token, refreshKey);
  const page = catalog.data ?? emptyPage;

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPageNumber(0);
    setFilters(Object.fromEntries([...new FormData(event.currentTarget).entries()]
      .filter(([, value]) => String(value).trim()).map(([key, value]) => [key, String(value)])));
  }

  function clearFilters() {
    filterForm.current?.reset();
    setFilters({});
    setPageNumber(0);
    filterForm.current?.querySelector<HTMLInputElement>('input[name="numero"]')?.focus();
  }

  return <section className="panel span"><h2>Processos Administrativos ativos</h2>
    <form ref={filterForm} className="inline-form" onSubmit={filter}><label>Filtrar por número<input name="numero" /></label>
      <label>Filtrar por origem<input name="origem" /></label><label>Filtrar por objeto<input name="objeto" /></label>
      <label>Filtrar por coordenador<input name="coordenador" /></label>
      <label>Filtrar por tipo<select name="tipo"><option value="">Todos</option>{opcoesDominio("tipoInstrumento").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Filtrar por status<select name="status"><option value="">Todos</option>{opcoesDominio("statusProcesso").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Filtrar por vigência<select name="vigencia"><option value="">Todas</option>{opcoesDominio("situacaoVigencia").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <button className="primary">Filtrar</button>
      {hasFilters && <button type="button" className="secondary-action" onClick={clearFilters}>Limpar filtros</button>}</form>
    <ResourceState {...catalog} onRetry={catalog.reload} label="os processos administrativos" />
    <div className="cards">{page.content.map(processo => <button key={processo.id}
      aria-pressed={selectedId === processo.id}
      className={`process-card ${selectedId === processo.id ? "selected" : ""}`} onClick={() => onSelect(processo)}>
      <div><strong>{processo.numero}</strong><small>{processo.origem} · {processo.numeroProjeto ?? "sem projeto"}</small></div>
      <Badge value={processo.status} /><small>Responsável: {processo.responsavel?.nome ?? "não atribuído"}</small>
      <small>Setor atual: {processo.setorAtual ?? "sem movimentação"}</small></button>)}</div>
    <div className="feedback-live" role="status" aria-atomic="true">
      {!catalog.loading && !catalog.error && !page.content.length && <p className="empty">
        {hasFilters ? "Nenhum Processo Administrativo corresponde aos filtros. Revise os filtros ou use Limpar filtros para ver os processos ativos."
          : page.totalElements === 0 ? "Nenhum Processo Administrativo ativo cadastrado. Use Novo Processo Administrativo para cadastrar um registro."
          : "Nenhum Processo Administrativo nesta página. Volte à página anterior."}
      </p>}
    </div>
    {page.totalPages > 0 && <div className="pagination"><button disabled={page.number === 0}
      onClick={() => setPageNumber(page.number - 1)}>Página anterior</button><span>Página {page.number + 1} de {page.totalPages}</span>
      <button disabled={page.number + 1 >= page.totalPages}
        onClick={() => setPageNumber(page.number + 1)}>Próxima página</button></div>}
  </section>;
}
