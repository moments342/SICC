import { FormEvent, useCallback, useEffect, useState } from "react";
import { request } from "../api";
import { Badge } from "../components/Presentation";
import { opcoesDominio, rotuloDominio } from "../domainLabels";
import type { Page, Publico } from "../models";
import type { Session } from "../session";

export function PublicAccess({ onLogin }: { onLogin: (s: Session) => void }) {
  const [page, setPage] = useState<Page<Publico>>({
    content: [], totalElements: 0, totalPages: 0, number: 0, size: 20
  });
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [pageNumber, setPageNumber] = useState(0);
  const [error, setError] = useState("");
  const [loginLoading, setLoginLoading] = useState(false);
  const query = new URLSearchParams({ page: String(pageNumber), size: "20", ...filters }).toString();
  const load = useCallback(() => request<Page<Publico>>(`/api/v1/public/processos?${query}`)
    .then(setPage).catch(e => setError(e.message)), [query]);
  useEffect(() => { void load(); }, [load]);

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

  return <div className="public-page">
    <section className="hero">
      <div className="brand light"><div><strong>SICC</strong><small>DIPAC · UFGD</small></div></div>
      <div><p className="eyebrow">Transparência institucional</p><h1>Processos e instrumentos<br />em um só lugar.</h1>
        <p>Acompanhe o status e as vigências dos instrumentos formalizados pela DIPAC.</p></div>
      <form className="login-card" onSubmit={login}><h2>Área interna</h2><p>Acesso exclusivo para a equipe DIPAC.</p>
        <label>Login<input name="login" autoComplete="username" required /></label>
        <label>Senha<input name="senha" type="password" autoComplete="current-password" required /></label>
        <button className="primary" disabled={loginLoading}>
          {loginLoading ? "Entrando…" : "Entrar no SICC"}
        </button>{error && <p className="error">{error}</p>}</form>
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
      <div className="table-wrap"><table><thead><tr><th>Processo Administrativo</th><th>Instrumento</th><th>Origem</th>
        <th>Coordenador</th><th>Status</th><th>Vigência contratual</th><th>Vigência TED</th></tr></thead>
        <tbody>{page.content.map(item => <tr key={item.numeroProcesso}><td><strong>{item.numeroProcesso}</strong></td>
          <td>{rotuloDominio(item.tipoInstrumento)}</td><td>{item.origem}</td><td>{item.coordenador}</td>
          <td><Badge value={item.status} /></td><td>{item.vigenciaContratualFinal ?? "—"}</td>
          <td>{item.vigenciaTedFinal ?? "—"}</td></tr>)}
          {!page.content.length && <tr><td colSpan={7} className="empty">Nenhum processo encontrado.</td></tr>}</tbody></table></div>
      {page.totalPages > 0 && <div className="pagination"><button disabled={page.number === 0}
        onClick={() => setPageNumber(page.number - 1)}>Página anterior</button>
        <span>Página {page.number + 1} de {page.totalPages}</span>
        <button disabled={page.number + 1 >= page.totalPages}
          onClick={() => setPageNumber(page.number + 1)}>Próxima página</button></div>}
    </section>
  </div>;
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
  return <div className="center-page"><form className="panel narrow" onSubmit={submit}><div className="brand">
    <span>S</span><div><strong>SICC</strong><small>Primeiro acesso</small></div></div><h1>Crie sua senha permanente</h1>
    <p>A senha temporária deve ser substituída antes de acessar o sistema.</p>
    <label>Senha temporária<input name="atual" type="password" required /></label>
    <label>Nova senha<input name="nova" type="password" minLength={10} required /></label>
    <button className="primary" disabled={loading}>
      {loading ? "Salvando…" : "Definir senha"}
    </button>{error && <p className="error">{error}</p>}</form></div>;
}
