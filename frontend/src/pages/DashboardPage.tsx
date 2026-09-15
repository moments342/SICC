import { FormEvent, useCallback, useEffect, useState } from "react";
import { request } from "../api";
import { formatarDataNegocio, formatarMes, formatarNumeroDias, money } from "../formatters";
import { opcoesDominio } from "../domainLabels";
import { Metric } from "../components/Presentation";
import type { DashboardData } from "../models";

export function Dashboard({ token }: { token: string }) {
  const [data, setData] = useState<DashboardData | null>(null);
  const [detalhe, setDetalhe] = useState<
    { tipo: "setor"; setor: string } | { tipo: "tempoInicial" } | null
  >(null);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const query = new URLSearchParams(filters).toString();
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const resultado = await request<DashboardData>(
        `/api/v1/dashboard${query ? `?${query}` : ""}`, {}, token
      );
      setData(resultado);
      setDetalhe(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [query, token]);
  useEffect(() => { void load(); }, [load]);

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
    ? data.detalhesPermanenciaPorSetor[detalhe.setor] ?? []
    : [];

  return <div className="dashboard-stack"><form className="panel dashboard-filters inline-form" onSubmit={filter}>
    <label>Origem do processo<input name="origem" /></label>
    <label>Tipo do instrumento<select name="tipo"><option value="">Todos</option>
      {opcoesDominio("tipoInstrumento").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
    <label>Status do processo<select name="status"><option value="">Todos</option>
      {opcoesDominio("statusProcesso").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
    <button className="primary" disabled={loading}>Aplicar filtros</button>
  </form>
  {loading && <section className="dashboard-state panel">Carregando painel…</section>}
  {!loading && error && <section className="dashboard-state panel error-state"><p>{error}</p>
    <button onClick={() => void load()}>Tentar novamente</button></section>}
  {!loading && !error && data && total === 0 && <section className="dashboard-state panel">
    Nenhum Processo Administrativo corresponde aos filtros.</section>}
  {!loading && !error && data && total > 0 && <><div className="metrics">
    <Metric label="Em formalização" value={data.processosPorStatus.EM_FORMALIZACAO ?? 0} />
    <Metric label="Em vigência" value={data.processosPorStatus.EM_VIGENCIA ?? 0} />
    <Metric label="Concluídos" value={data.processosPorStatus.CONCLUIDO ?? 0} />
    <Metric label="Percentual concluído" value={`${data.percentualConcluidos.toFixed(1)}%`} />
  </div><div className="grid two"><section className="panel"><h2>Vigências</h2>
    <div className="alert-row"><span>Contratual · próximos 120 dias</span><strong>{data.alertasContratuais}</strong></div>
    <div className="alert-row"><span>TED · próximos 120 dias</span><strong>{data.alertasTed}</strong></div>
    <div className="alert-row"><span>Valor total vigente</span><strong>{money(data.valorTotalVigente)}</strong></div>
  </section><section className="panel"><h2>Tramitação</h2>
    <button type="button" className="alert-row dashboard-indicator"
      disabled={!data.maiorGargalo}
      aria-label={`Maior gargalo · ${data.maiorGargalo ?? "Sem dados"}`}
      onClick={() => data.maiorGargalo && setDetalhe({ tipo: "setor", setor: data.maiorGargalo })}>
      <span>Maior gargalo</span><strong>{data.maiorGargalo ?? "Sem dados"}</strong></button>
    <button type="button" className="alert-row dashboard-indicator"
      aria-label={`Tempo inicial médio · ${formatarNumeroDias(data.tempoMedioTramitacaoInicialDias)} dias`}
      onClick={() => setDetalhe({ tipo: "tempoInicial" })}>
      <span>Tempo inicial médio</span><strong>{formatarNumeroDias(data.tempoMedioTramitacaoInicialDias)} dias</strong></button>
    {Object.entries(data.permanenciaMediaPorSetor).map(([name, days]) =>
      <button type="button" className="bar dashboard-indicator" key={name}
        aria-label={`${name} · média de ${formatarNumeroDias(days)} dias`}
        onClick={() => setDetalhe({ tipo: "setor", setor: name })}>
        <span>{name}</span><i style={{ width: `${Math.min(100, days * 2)}%` }} />
        <b>{formatarNumeroDias(days)}d</b></button>)}
  </section></div><div className="grid two dashboard-details">
    <section className="panel"><h2>Instrumentos por tipo</h2>
      {opcoesDominio("tipoInstrumento").map(opcao =>
        <div className="alert-row" key={opcao.codigo}><span>{opcao.rotulo}</span>
          <strong>{data.instrumentosPorTipo[opcao.codigo] ?? 0}</strong></div>)}
    </section>
    <section className="panel"><h2>Atividade mensal</h2>
      <div className="table-wrap"><table><thead><tr><th>Mês</th><th>Formalizações</th><th>Conclusões</th></tr></thead>
        <tbody>{meses.map(mes => <tr key={mes}><td>{formatarMes(mes)}</td>
          <td>{data.formalizacoesMensais[mes] ?? 0}</td><td>{data.conclusoesMensais[mes] ?? 0}</td></tr>)}</tbody>
      </table></div>
    </section>
  </div>
  {detalhe?.tipo === "setor" && <section className="panel dashboard-metric-details">
    <div className="panel-title"><div><h2>Permanências em {detalhe.setor}</h2>
      <p className="muted">Períodos usados na média exibida, incluindo permanências ainda abertas.</p></div>
      <button aria-label="Fechar detalhamento" onClick={() => setDetalhe(null)}>×</button></div>
    <div className="table-wrap"><table><thead><tr><th>Processo Administrativo</th><th>Período</th>
      <th>Permanência</th><th>Situação</th></tr></thead><tbody>
      {permanenciasDetalhadas.map((item, indice) => <tr
        key={`${item.processoId}-${item.dataChegada}-${indice}`}><td><strong>{item.numeroProcesso}</strong></td>
        <td>{formatarDataNegocio(item.dataChegada)} a {item.aberta
          ? "hoje" : formatarDataNegocio(item.dataSaida!)}</td>
        <td>{item.diasCorridos} dias</td><td>{item.aberta ? "Aberta" : "Encerrada"}</td></tr>)}
    </tbody></table></div>
  </section>}
  {detalhe?.tipo === "tempoInicial" && <section className="panel dashboard-metric-details">
    <div className="panel-title"><div><h2>Tempo de Tramitação Inicial</h2>
      <p className="muted">Todos os processos participam da média, inclusive os ainda não formalizados.</p></div>
      <button aria-label="Fechar detalhamento" onClick={() => setDetalhe(null)}>×</button></div>
    <div className="table-wrap"><table><thead><tr><th>Processo Administrativo</th><th>Cadastro</th>
      <th>Formalização</th><th>Tempo inicial</th></tr></thead><tbody>
      {data.detalhesTempoTramitacaoInicial.map(item => <tr key={item.processoId}>
        <td><strong>{item.numeroProcesso}</strong></td><td>{formatarDataNegocio(item.dataCadastro)}</td>
        <td>{item.aberta ? "Ainda não formalizado" : formatarDataNegocio(item.dataFormalizacao!)}</td>
        <td>{item.aberta ? "Aberto" : "Formalizado"} · {item.diasCorridos} dias</td></tr>)}
    </tbody></table></div>
  </section>}
  </>}</div>;
}
