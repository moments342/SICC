import { FormEvent, useEffect, useRef } from "react";
import { FilterDisclosure } from "../components/FilterDisclosure";
import { useResource } from "../useResource";
import { useRouteState } from "../useRouteState";
import { AppliedFilters, resetFilterField } from "../components/AppliedFilters";
import { Badge } from "../components/Presentation";
import { opcoesDominio, rotuloDominio } from "../domainLabels";
import type { Page, RegistroAuditoria } from "../models";
export function AuditRecords({ token }: { token: string }) {
  const keys = ["acao", "resultado", "usuario", "dataInicial", "dataFinal"];
  const [route, updateRoute] = useRouteState("auditoria", [...keys, "pagina"]);
  const { pagina, ...filters } = route;
  const pageNumber = Math.max(0, Number.parseInt(pagina, 10) || 0);
  const setPageNumber = (value: number) => updateRoute({ pagina: value ? String(value) : "" });
  const setFilters = (value: Record<string, string>) => updateRoute({ ...Object.fromEntries(keys.map(key => [key, ""])), ...value, pagina: "" });
  const filterForm = useRef<HTMLFormElement>(null);
  const query = new URLSearchParams({
    page: String(pageNumber), size: "20", ...filters
  }).toString();
  const resource = useResource<Page<RegistroAuditoria>>(`/api/v1/auditoria?${query}`, token);
  const { loading, error, reload: load } = resource;
  const page = resource.data ?? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
  useEffect(() => {
    for (const key of keys) {
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

  return <section className="panel audit-panel">
    <div className="panel-title"><div><h2>Registros de Auditoria</h2>
      <p className="muted">Registros imutáveis de autenticações e ações administrativas.</p></div>
      {!loading && !error && <strong>{page.totalElements} registro(s)</strong>}</div>
    <form ref={filterForm} className="audit-filters" onSubmit={filter}>
      <FilterDisclosure label="Filtros da auditoria" count={Object.keys(filters).length}>
      <label>Ação<input name="acao" placeholder="Ex.: LOGIN" /></label>
      <label>Resultado<select name="resultado"><option value="">Todos</option>
        {opcoesDominio("resultadoAuditoria").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Usuário<input name="usuario" placeholder="Nome ou login" /></label>
      <label>Data inicial<input name="dataInicial" type="date" /></label>
      <label>Data final<input name="dataFinal" type="date" /></label>
      <button className="primary">Aplicar filtros</button>
      {Object.keys(filters).length > 0 && <button type="button" className="secondary-action" onClick={() => { filterForm.current?.reset(); setPageNumber(0); setFilters({}); }}>Limpar filtros</button>}
      </FilterDisclosure>
    </form>
    <AppliedFilters values={filters} onRemove={key => { resetFilterField(filterForm.current, key); updateRoute({ [key]: "", pagina: "" }); }} />

    {loading && <div className="audit-state" role="status">Carregando registros de auditoria…</div>}
    {!loading && error && <div className="audit-state error-state" role="alert">
      <strong>Não foi possível carregar os registros de auditoria.</strong><p>{error}</p>
      <button onClick={() => void load()}>Tentar novamente</button>
    </div>}
    {!loading && !error && !page.content.length &&
      <div className="audit-state"><strong>Nenhum registro de auditoria encontrado.</strong>
        <p>Altere os filtros para ampliar a consulta.</p></div>}
    {!loading && !error && page.content.length > 0 && <>
      <div className="table-wrap audit-table"><table><thead><tr><th>Data e hora</th><th>Ação</th><th>Resultado</th>
        <th>Ator</th><th>Objeto afetado</th><th>Informações adicionais</th></tr></thead>
        <tbody>{page.content.map(event => <tr key={event.id}>
          <td className="audit-when" data-label="Data e hora">{new Date(event.criadoEm).toLocaleString("pt-BR")}</td>
          <td className="audit-action" data-label="Ação"><strong>{rotuloDominio(event.acao)}</strong></td>
          <td className="audit-result" data-label="Resultado"><Badge value={event.resultado} /></td>
          <td className="audit-person" data-label="Ator">{event.ator ? <div className="audit-actor"><strong>{event.ator.nome}</strong>
            <small>@{event.ator.login}</small></div> : <span className="muted">Ator não identificado</span>}</td>
          <td className="audit-target" data-label="Objeto afetado"><div className="audit-object"><strong>{rotuloDominio(event.objeto.tipo)}</strong>
            {event.objeto.id != null && <small>#{event.objeto.id}</small>}</div></td>
          <td className="audit-more"><details><summary aria-label={`Ver detalhes do evento ${event.id}`}>Ver detalhes</summary>
            <dl><dt>Detalhes</dt><dd>{event.detalhes ?? "—"}</dd><dt>Origem</dt><dd>{event.ipOrigem ?? "—"}</dd></dl>
          </details></td>
        </tr>)}</tbody></table></div>
      {page.totalPages > 1 && <div className="pagination"><button disabled={page.number === 0}
        onClick={() => setPageNumber(page.number - 1)}>Página anterior</button>
        <span>Página {page.number + 1} de {page.totalPages}</span>
        <button disabled={page.number + 1 >= page.totalPages}
          onClick={() => setPageNumber(page.number + 1)}>Próxima página</button></div>}
    </>}
  </section>;
}
