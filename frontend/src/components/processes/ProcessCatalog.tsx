import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { request } from "../../api";
import { responsavelSelecionado } from "../../formValues";
import { opcoesDominio, rotuloDominio } from "../../domainLabels";
import type { Page, ProcessoAdministrativo, ResponsavelProcesso } from "../../models";
import { Badge } from "../Presentation";

const emptyPage: Page<ProcessoAdministrativo> = {
  content: [], totalElements: 0, totalPages: 0, number: 0, size: 20
};

type CommonProps = { token: string; notify: (message: string) => void };

export function ProcessCreationPanel({ token, notify, responsaveis, onCreated }: CommonProps & {
  responsaveis: ResponsavelProcesso[];
  onCreated: () => void;
}) {
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    try {
      await request("/api/v1/processos", { method: "POST", body: JSON.stringify({
        numero: data.get("numero"), origem: data.get("origem"), numeroProjeto: data.get("projeto"),
        responsavelId: responsavelSelecionado(data)
      }) }, token);
      form.reset();
      notify("Processo Administrativo criado.");
      onCreated();
    } catch (error) { notify((error as Error).message); }
  }

  return <section className="panel"><h2>Novo Processo Administrativo</h2>
    <form className="stack" onSubmit={create}><label>Número<input name="numero" required /></label>
      <label>Origem<input name="origem" required /></label><label>Número do projeto<input name="projeto" /></label>
      <label>Responsável DIPAC<select name="responsavel"><option value="">Sem responsável</option>
        {responsaveis.map(responsavel => <option key={responsavel.id} value={responsavel.id}>
          {responsavel.nome} · {rotuloDominio(responsavel.perfil)}
        </option>)}</select></label>
      <button className="primary">Cadastrar processo</button></form></section>;
}

export function ProcessCatalogPanel({ token, notify, selectedId, refreshKey, onSelect }: CommonProps & {
  selectedId?: number;
  refreshKey: number;
  onSelect: (processo: ProcessoAdministrativo) => void;
}) {
  const [page, setPage] = useState<Page<ProcessoAdministrativo>>(emptyPage);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [pageNumber, setPageNumber] = useState(0);
  const query = useMemo(() => new URLSearchParams({ page: String(pageNumber), size: "20", ...filters }).toString(),
    [filters, pageNumber]);
  const load = useCallback(async (signal?: AbortSignal) => {
    setPage(await request<Page<ProcessoAdministrativo>>(`/api/v1/processos?${query}`, { signal }, token));
  }, [query, token]);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal).catch(error => {
      if ((error as Error).name !== "AbortError") notify((error as Error).message);
    });
    return () => controller.abort();
  }, [load, notify, refreshKey]);

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPageNumber(0);
    setFilters(Object.fromEntries([...new FormData(event.currentTarget).entries()]
      .filter(([, value]) => String(value).trim()).map(([key, value]) => [key, String(value)])));
  }

  return <section className="panel span"><h2>Processos Administrativos ativos</h2>
    <form className="inline-form" onSubmit={filter}><label>Filtrar por número<input name="numero" /></label>
      <label>Filtrar por origem<input name="origem" /></label><label>Filtrar por objeto<input name="objeto" /></label>
      <label>Filtrar por coordenador<input name="coordenador" /></label>
      <label>Filtrar por tipo<select name="tipo"><option value="">Todos</option>{opcoesDominio("tipoInstrumento").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Filtrar por status<select name="status"><option value="">Todos</option>{opcoesDominio("statusProcesso").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Filtrar por vigência<select name="vigencia"><option value="">Todas</option>{opcoesDominio("situacaoVigencia").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <button className="primary">Filtrar</button></form>
    <div className="cards">{page.content.map(processo => <button key={processo.id}
      className={`process-card ${selectedId === processo.id ? "selected" : ""}`} onClick={() => onSelect(processo)}>
      <div><strong>{processo.numero}</strong><small>{processo.origem} · {processo.numeroProjeto ?? "sem projeto"}</small></div>
      <Badge value={processo.status} /><small>Responsável: {processo.responsavel?.nome ?? "não atribuído"}</small>
      <small>Setor atual: {processo.setorAtual ?? "sem movimentação"}</small></button>)}</div>
    {!page.content.length && <p className="empty">Cadastre o primeiro Processo Administrativo.</p>}
    {page.totalPages > 0 && <div className="pagination"><button disabled={page.number === 0}
      onClick={() => setPageNumber(page.number - 1)}>Página anterior</button><span>Página {page.number + 1} de {page.totalPages}</span>
      <button disabled={page.number + 1 >= page.totalPages}
        onClick={() => setPageNumber(page.number + 1)}>Próxima página</button></div>}
  </section>;
}
