import { FormEvent, useState } from "react";
import { request } from "../api";
import { useResource } from "../useResource";
import { ResourceState } from "../components/Feedback";
import { Badge } from "../components/Presentation";
import { opcoesDominio, rotuloDominio } from "../domainLabels";
import type { Page, Publico } from "../models";
import type { Session } from "../session";

export function PublicAccess({ onLogin }: { onLogin: (s: Session) => void }) {
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [pageNumber, setPageNumber] = useState(0);
  const [error, setError] = useState("");
  const [loginLoading, setLoginLoading] = useState(false);
  const query = new URLSearchParams({ page: String(pageNumber), size: "20", ...filters }).toString();
  const catalog = useResource<Page<Publico>>(`/api/v1/public/processos?${query}`);
  const page = catalog.data;

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = Object.fromEntries([...new FormData(event.currentTarget).entries()]
      .filter(([, value]) => String(value).trim()).map(([key, value]) => [key, String(value)]));
    setPageNumber(0);
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

  return <main className="public-page">
    <section className="hero">
      <div className="brand light"><div><strong>SICC</strong><small>DIPAC · UFGD</small></div></div>
      <div><p className="eyebrow">Transparência institucional</p><h1>Processos e instrumentos<br />em um só lugar.</h1>
        <p>Acompanhe o status e as vigências dos instrumentos formalizados pela DIPAC.</p></div>
      <form className="login-card" onSubmit={login}><h2>Área interna</h2><p>Acesso exclusivo para a equipe DIPAC.</p>
        <label>Login<input name="login" autoComplete="username" aria-describedby={error ? "login-error" : undefined} required /></label>
        <label>Senha<input name="senha" type="password" autoComplete="current-password" aria-describedby={error ? "login-error" : undefined} required /></label>
        <button className="primary" disabled={loginLoading}>
          {loginLoading ? "Entrando…" : "Entrar no SICC"}
        </button><div className="feedback-live" role="alert" aria-atomic="true" id="login-error">{error && <p className="error">{error}</p>}</div></form>
    </section>
    <section className="public-list"><div className="section-title"><div><p className="eyebrow">Consulta pública</p>
      <h2>Processos Administrativos</h2></div></div>
      <form className="inline-form public-filters" onSubmit={filter}><label>Número<input name="numero" /></label>
        <label>Origem<input name="origem" /></label>
        <label>Tipo<select name="tipo"><option value="">Todos</option>
          {opcoesDominio("tipoInstrumento").map(opcao =>
            <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
        <label>Status<select name="status"><option value="">Todos</option>
          {opcoesDominio("statusProcesso").map(opcao =>
            <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
        <label>Vigência<select name="vigencia"><option value="">Todas</option>
          {opcoesDominio("situacaoVigencia").map(opcao =>
            <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
        <button className="primary">Filtrar</button></form>
      <ResourceState {...catalog} onRetry={catalog.reload} label="a consulta pública" />
      <div className="table-wrap"><table><thead><tr><th>Processo Administrativo</th><th>Instrumento</th><th>Origem</th>
        <th>Coordenador</th><th>Status</th><th>Vigência contratual</th><th>Vigência TED</th></tr></thead>
        <tbody>{page?.content.map(item => <tr key={item.numeroProcesso}><td><strong>{item.numeroProcesso}</strong></td>
          <td>{rotuloDominio(item.tipoInstrumento)}</td><td>{item.origem}</td><td>{item.coordenador}</td>
          <td><Badge value={item.status} /></td><td>{item.vigenciaContratualFinal ?? "—"}</td>
          <td>{item.vigenciaTedFinal ?? "—"}</td></tr>)}
          {page && !page.content.length && <tr><td colSpan={7} className="empty">Nenhum processo encontrado.</td></tr>}</tbody></table></div>
      {page && page.totalPages > 0 && <div className="pagination"><button disabled={page.number === 0}
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
    <label>Nova senha<input name="nova" type="password" autoComplete="new-password" minLength={10}
      aria-describedby={error ? "password-error" : undefined} required /></label>
    <button className="primary" disabled={loading}>
      {loading ? "Salvando…" : "Definir senha"}
    </button><div className="feedback-live" role="alert" aria-atomic="true" id="password-error">{error && <p className="error">{error}</p>}</div></form></main>;
}
