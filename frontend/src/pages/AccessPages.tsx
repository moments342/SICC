import { formatarDataNegocio } from "../formatters";
import { FormEvent, useEffect, useRef, useState } from "react";
import { FilterDisclosure } from "../components/FilterDisclosure";
import { Modal } from "../components/Modal";
import { request } from "../api";
import { useResource } from "../useResource";
import { ResourceState } from "../components/Feedback";
import { Badge } from "../components/Presentation";
import { NewPasswordField } from "../components/NewPasswordField";
import { AppliedFilters, resetFilterField } from "../components/AppliedFilters";
import { useRouteState } from "../useRouteState";
import { opcoesDominio, rotuloDominio } from "../domainLabels";
import type { Page, Publico } from "../models";
import type { Session } from "../session";

export function PublicAccess({ onLogin }: { onLogin: (s: Session) => void }) {
  const [loginOpen, setLoginOpen] = useState(false);
  const keys = ["numero", "origem", "tipo", "status", "vigencia"];
  const [route, updateRoute] = useRouteState("consulta-publica", [...keys, "pagina"]);
  const { pagina, ...filters } = route;
  const pageNumber = Math.max(0, Number.parseInt(pagina, 10) || 0);
  const setPageNumber = (value: number) => updateRoute({ pagina: value ? String(value) : "" });
  const setFilters = (value: Record<string, string>) => updateRoute({ ...Object.fromEntries(keys.map(key => [key, ""])), ...value, pagina: "" });
  const [error, setError] = useState("");
  const [loginLoading, setLoginLoading] = useState(false);
  const filterForm = useRef<HTMLFormElement>(null);
  const hasFilters = Object.keys(filters).length > 0;
  const query = new URLSearchParams({ page: String(pageNumber), size: "20", ...filters }).toString();
  const catalog = useResource<Page<Publico>>(`/api/v1/public/processos?${query}`);
  const page = catalog.data;
  useEffect(() => {
    for (const key of keys) {
      const field = filterForm.current?.elements.namedItem(key);
      if (field instanceof HTMLInputElement || field instanceof HTMLSelectElement) field.value = filters[key] ?? "";
    }
  }, [query]);

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = Object.fromEntries([...new FormData(event.currentTarget).entries()]
      .filter(([, value]) => String(value).trim()).map(([key, value]) => [key, String(value)]));
    setFilters(values);
  }

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setError("");
    setLoginLoading(true);
    try {
      onLogin(await request<Session>("/api/v1/auth/login", {
        method: "POST", body: JSON.stringify({ login: data.get("login"), senha: data.get("senha") })
      }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoginLoading(false);
    }
  }

  function clearFilters() {
    filterForm.current?.reset(); setFilters({}); setPageNumber(0);
    filterForm.current?.querySelector<HTMLInputElement>('[name="numero"]')?.focus();
  }

  return <main className="public-page">
    <header className="public-header"><a href="#consulta-publica" className="brand light" aria-label="Consultar Processos Administrativos"><div><strong>SICC</strong><small>DIPAC · UFGD</small></div></a>
      <a href="#acesso-interno" onClick={event => { event.preventDefault(); setLoginOpen(true); }}>Acesso interno</a></header>
    <Modal open={loginOpen} title="Área interna" description="Acesso exclusivo para a equipe DIPAC." onClose={() => setLoginOpen(false)} closeLabel="Fechar acesso interno" busy={loginLoading}>
      <form id="acesso-interno" className="login-card login-open" onSubmit={login}>
        <label>Login<input name="login" autoComplete="username" aria-describedby={error ? "login-error" : undefined} required /></label>
        <label>Senha<input name="senha" type="password" autoComplete="current-password" aria-describedby={error ? "login-error" : undefined} required /></label>
        <button className="primary" disabled={loginLoading}>
          {loginLoading ? "Entrando…" : "Entrar no SICC"}
        </button><div className="feedback-live" role="alert" aria-atomic="true" id="login-error">{error && <p className="error">{error}</p>}</div></form>
    </Modal>
    <section id="consulta-publica" className="public-list"><div className="section-title"><div><h1>Consulta pública</h1>
      <p>Consulte Processos Administrativos, inclusive os ainda em formalização, e as vigências de seus instrumentos.</p></div></div>
      <form ref={filterForm} className="compact-filters public-filters" onSubmit={filter}>
        <div className="inline-form filter-search"><label>Número<input name="numero" type="search" placeholder="Número do Processo Administrativo" /></label>
        <button className="primary">Filtrar</button>{hasFilters && <button type="button" className="secondary-action" onClick={clearFilters}>Limpar filtros</button>}</div>
        <FilterDisclosure count={Object.keys(filters).filter(name => name !== "numero").length}>
        <label>Origem<input name="origem" /></label>
        <label>Tipo<select name="tipo"><option value="">Todos</option>
          {opcoesDominio("tipoInstrumento").map(opcao =>
            <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
        <label>Status<select name="status"><option value="">Todos</option>
          {opcoesDominio("statusProcesso").map(opcao =>
            <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
        <label>Vigência (contratual ou TED)<select name="vigencia"><option value="">Todas</option>
          {opcoesDominio("situacaoVigencia").map(opcao =>
            <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
        <button className="secondary-action">Aplicar filtros</button></FilterDisclosure></form>
      <AppliedFilters values={filters} onRemove={key => { resetFilterField(filterForm.current, key); updateRoute({ [key]: "", pagina: "" }); }} />
      {page && !catalog.loading && !catalog.error && <p className="result-count" role="status">{page.totalElements} {page.totalElements === 1 ? "Processo Administrativo encontrado" : "Processos Administrativos encontrados"}</p>}
      <ResourceState {...catalog} onRetry={catalog.reload} label="a consulta pública" />
      <div className="table-wrap"><table><thead><tr><th>Processo Administrativo</th><th>Instrumento</th><th>Origem</th>
        <th>Coordenador</th><th>Status</th><th>Vigência contratual</th><th>Vigência TED</th></tr></thead>
        <tbody>{page?.content.map(item => <tr key={item.numeroProcesso}><td data-label="Processo Administrativo"><strong>{item.numeroProcesso}</strong></td>
          <td data-label="Instrumento">{rotuloDominio(item.tipoInstrumento)}</td><td data-label="Origem">{item.origem}</td><td className="public-coordinator" data-label="Coordenador">{item.coordenador}</td>
          <td data-label="Status"><Badge value={item.status} /></td><td data-label="Vigência contratual">{item.vigenciaContratualFinal ? formatarDataNegocio(item.vigenciaContratualFinal) : "Não informada"}</td>
          <td data-label="Vigência TED">{item.vigenciaTedFinal ? formatarDataNegocio(item.vigenciaTedFinal) : "Não informada"}</td></tr>)}
          {page && !catalog.loading && !catalog.error && !page.content.length && <tr><td colSpan={7} className="empty">{hasFilters ? "Nenhum Processo Administrativo corresponde aos filtros. Use Limpar filtros para ampliar a consulta." : "Nenhum Processo Administrativo disponível para consulta."}</td></tr>}</tbody></table></div>
      {page && page.totalPages > 1 && <div className="pagination"><button disabled={page.number === 0}
        onClick={() => setPageNumber(page.number - 1)}>Página anterior</button>
        <span>Página {page.number + 1} de {page.totalPages}</span>
        <button disabled={page.number + 1 >= page.totalPages}
          onClick={() => setPageNumber(page.number + 1)}>Próxima página</button></div>}
    </section>
  </main>;
}

export function PasswordChange({ session, onDone }: { session: Session; onDone: () => void }) {
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setError("");
    setLoading(true);
    try {
      await request("/api/v1/auth/senha", { method: "POST", body: JSON.stringify({
        senhaAtual: data.get("atual"), novaSenha: data.get("nova")
      }) }, session.token);
      onDone();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }
  return <main className="center-page"><form className="panel narrow" onSubmit={submit}><div className="brand">
    <span>S</span><div><strong>SICC</strong><small>Primeiro acesso</small></div></div><h1>Crie sua senha permanente</h1>
    <p>A senha temporária deve ser substituída antes de acessar o sistema.</p>
    <label>Senha temporária<input name="atual" type="password" autoComplete="current-password"
      aria-describedby={error ? "password-error" : undefined} required /></label>
    <NewPasswordField label="Nova senha" name="nova" errorId={error ? "password-error" : undefined} />
    <button className="primary" disabled={loading}>
      {loading ? "Salvando…" : "Definir senha"}
    </button><div className="feedback-live" role="alert" aria-atomic="true" id="password-error">{error && <p className="error">{error}</p>}</div></form></main>;
}
