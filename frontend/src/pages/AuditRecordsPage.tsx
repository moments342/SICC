import { FormEvent, useCallback, useEffect, useState } from "react";
import { request } from "../api";
import { Badge } from "../components/Presentation";
import { opcoesDominio, rotuloDominio } from "../domainLabels";
import type { Page, RegistroAuditoria } from "../models";
export function AuditRecords({ token }: { token: string }) {
  const [page, setPage] = useState<Page<RegistroAuditoria>>({
    content: [], totalElements: 0, totalPages: 0, number: 0, size: 20
  });
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [pageNumber, setPageNumber] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const query = new URLSearchParams({
    page: String(pageNumber), size: "20", ...filters
  }).toString();
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setPage(await request<Page<RegistroAuditoria>>(`/api/v1/auditoria?${query}`, {}, token));
    } catch (requestError) {
      setError((requestError as Error).message);
    } finally {
      setLoading(false);
    }
  }, [query, token]);
  useEffect(() => { void load(); }, [load]);

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPageNumber(0);
    setFilters(Object.fromEntries([...new FormData(event.currentTarget).entries()]
      .filter(([, value]) => String(value).trim())
      .map(([key, value]) => [key, String(value).trim()])));
  }

  return <section className="panel audit-panel">
    <div className="panel-title"><div><h2>Registros de Auditoria</h2>
      <p className="muted">Registros imutáveis de autenticações e ações administrativas.</p></div>
      {!loading && !error && <strong>{page.totalElements} registro(s)</strong>}</div>
    <form className="inline-form audit-filters" onSubmit={filter}>
      <label>Ação<input name="acao" placeholder="Ex.: LOGIN" /></label>
      <label>Resultado<select name="resultado"><option value="">Todos</option>
        {opcoesDominio("resultadoAuditoria").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Usuário<input name="usuario" placeholder="Nome ou login" /></label>
      <label>Data inicial<input name="dataInicial" type="date" /></label>
      <label>Data final<input name="dataFinal" type="date" /></label>
      <button className="primary">Aplicar filtros</button>
    </form>

    {loading && <div className="audit-state" role="status">Carregando registros de auditoria…</div>}
    {!loading && error && <div className="audit-state error-state" role="alert">
      <strong>Não foi possível carregar os registros de auditoria.</strong><p>{error}</p>
      <button onClick={() => void load()}>Tentar novamente</button>
    </div>}
    {!loading && !error && !page.content.length &&
      <div className="audit-state"><strong>Nenhum registro de auditoria encontrado.</strong>
        <p>Altere os filtros para ampliar a consulta.</p></div>}
    {!loading && !error && page.content.length > 0 && <>
      <div className="table-wrap"><table><thead><tr><th>Data e hora</th><th>Ação</th><th>Resultado</th>
        <th>Ator</th><th>Objeto afetado</th><th>Detalhes</th><th>Origem</th></tr></thead>
        <tbody>{page.content.map(event => <tr key={event.id}>
          <td>{new Date(event.criadoEm).toLocaleString("pt-BR")}</td>
          <td><strong>{rotuloDominio(event.acao)}</strong></td><td><Badge value={event.resultado} /></td>
          <td>{event.ator ? <div className="audit-actor"><strong>{event.ator.nome}</strong>
            <small>@{event.ator.login}</small></div> : <span className="muted">Ator não identificado</span>}</td>
          <td><div className="audit-object"><strong>{rotuloDominio(event.objeto.tipo)}</strong>
            {event.objeto.id != null && <small>#{event.objeto.id}</small>}</div></td>
          <td>{event.detalhes ?? "—"}</td><td>{event.ipOrigem ?? "—"}</td>
        </tr>)}</tbody></table></div>
      <div className="pagination"><button disabled={page.number === 0}
        onClick={() => setPageNumber(page.number - 1)}>Página anterior</button>
        <span>Página {page.number + 1} de {page.totalPages}</span>
        <button disabled={page.number + 1 >= page.totalPages}
          onClick={() => setPageNumber(page.number + 1)}>Próxima página</button></div>
    </>}
  </section>;
}
