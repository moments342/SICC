import { FormEvent, useCallback, useEffect, useState } from "react";
import { download, request, Session, upload } from "./api";

type Page<T> = { content: T[]; totalElements: number };
type ProcessoAdministrativo = {
  id: number; numero: string; origem: string; numeroProjeto?: string; status: string;
  setorAtual?: string; instrumento?: Instrumento;
};
type Instrumento = {
  id: number; numero: string; tipo: string; coordenador: string; valorAtual: number;
  vigenciaContratualFinal: string; vigenciaTedFinal?: string;
};
type Setor = { id: number; sigla: string; nome: string; ativo: boolean };
type Documento = { id: number; titulo: string; categoria: string; versoes: { versao: number; nomeArquivo: string }[] };
type Publico = {
  numeroProcesso: string; tipoInstrumento: string; origem: string; coordenador: string;
  status: string; vigenciaContratualFinal?: string; vigenciaTedFinal?: string;
};

type DashboardData = {
  processosPorStatus: Record<string, number>; percentualConcluidos: number;
  alertasContratuais: number; alertasTed: number; valorTotalVigente: number;
  instrumentosPorTipo: Record<string, number>; permanenciaMediaPorSetor: Record<string, number>;
  maiorGargalo: string | null; tempoMedioTramitacaoInicialDias: number;
  formalizacoesMensais: Record<string, number>; conclusoesMensais: Record<string, number>;
};

const emptyDashboard: DashboardData = {
  processosPorStatus: {}, percentualConcluidos: 0, alertasContratuais: 0,
  alertasTed: 0, valorTotalVigente: 0, instrumentosPorTipo: {},
  permanenciaMediaPorSetor: {}, maiorGargalo: null, tempoMedioTramitacaoInicialDias: 0,
  formalizacoesMensais: {}, conclusoesMensais: {}
};

export default function App() {
  const [session, setSession] = useState<Session | null>(() => {
    const saved = localStorage.getItem("sicc-session");
    return saved ? JSON.parse(saved) : null;
  });
  const [tab, setTab] = useState("dashboard");
  const [message, setMessage] = useState("");

  function saveSession(value: Session | null) {
    setSession(value);
    if (value) localStorage.setItem("sicc-session", JSON.stringify(value));
    else localStorage.removeItem("sicc-session");
  }

  if (!session) return <PublicAccess onLogin={saveSession} />;
  if (session.trocaSenhaObrigatoria) {
    return <PasswordChange session={session} onDone={() => saveSession(null)} />;
  }

  return (
    <div className="shell">
      <aside>
        <div className="brand"><span>S</span><div><strong>SICC</strong><small>DIPAC · UFGD</small></div></div>
        <nav>
          {["dashboard", "processos", "documentos", "alteracoes", "relatorios", "notificacoes"].map(item =>
            <button key={item} className={tab === item ? "active" : ""} onClick={() => setTab(item)}>
              {labels[item]}
            </button>
          )}
          {session.perfil === "ADMINISTRADOR_DIPAC" &&
            <button className={tab === "administracao" ? "active" : ""} onClick={() => setTab("administracao")}>
              Administração
            </button>}
          <button onClick={() => saveSession(null)}>Sair</button>
        </nav>
        <div className="profile"><span>{session.perfil === "ADMINISTRADOR_DIPAC" ? "AD" : "OP"}</span>
          <small>{session.perfil.replaceAll("_", " ")}</small></div>
      </aside>
      <main>
        <header><div><small>Sistema Integrado de Controle de Contratos</small><h1>{labels[tab]}</h1></div></header>
        {message && <div className="toast" onClick={() => setMessage("")}>{message}</div>}
        {tab === "dashboard" && <Dashboard token={session.token} />}
        {tab === "processos" && <Processes token={session.token} notify={setMessage} />}
        {tab === "documentos" && <Documents token={session.token} notify={setMessage} />}
        {tab === "alteracoes" && <Alterations token={session.token} notify={setMessage} />}
        {tab === "relatorios" && <Reports token={session.token} notify={setMessage} />}
        {tab === "notificacoes" && <Notifications token={session.token} />}
        {tab === "administracao" && <Administration token={session.token} notify={setMessage} />}
      </main>
    </div>
  );
}

const labels: Record<string, string> = {
  dashboard: "Visão geral", processos: "Processos Administrativos", documentos: "Documentos",
  alteracoes: "Alterações contratuais", relatorios: "Relatórios", notificacoes: "Notificações"
};

function PublicAccess({ onLogin }: { onLogin: (s: Session) => void }) {
  const [items, setItems] = useState<Publico[]>([]);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [loginLoading, setLoginLoading] = useState(false);
  const query = new URLSearchParams(filters).toString();
  const load = useCallback(() => request<Page<Publico>>(`/api/v1/public/processos?${query}`)
    .then(page => setItems(page.content)).catch(e => setError(e.message)), [query]);
  useEffect(() => { void load(); }, [load]);

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

  return <div className="public-page">
    <section className="hero">
      <div className="brand light"><span>S</span><div><strong>SICC</strong><small>DIPAC · UFGD</small></div></div>
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
      <form className="inline-form" onSubmit={filter}><label>Número<input name="numero" /></label>
        <label>Origem<input name="origem" /></label><label>Tipo<select name="tipo"><option value="">Todos</option>
          {["CONTRATO_GESTAO", "CONVENIO", "ACORDO_PARCERIA", "ACORDO_COOPERACAO_TECNICA"].map(x =>
            <option key={x}>{x}</option>)}</select></label>
        <label>Status<select name="status"><option value="">Todos</option>
          {["EM_FORMALIZACAO", "EM_VIGENCIA", "CONCLUIDO"].map(x => <option key={x}>{x}</option>)}</select></label>
        <label>Vigência<select name="vigencia"><option value="">Todas</option>
          {["VALIDA", "PROXIMA_VENCIMENTO", "VENCIDA", "NAO_INFORMADA"].map(x => <option key={x}>{x}</option>)}</select></label>
        <button className="primary">Filtrar</button></form>
      <div className="table-wrap"><table><thead><tr><th>Processo Administrativo</th><th>Instrumento</th><th>Origem</th>
        <th>Coordenador</th><th>Status</th><th>Vigência contratual</th><th>Vigência TED</th></tr></thead>
        <tbody>{items.map(item => <tr key={item.numeroProcesso}><td><strong>{item.numeroProcesso}</strong></td>
          <td>{item.tipoInstrumento.replaceAll("_", " ")}</td><td>{item.origem}</td><td>{item.coordenador}</td>
          <td><Badge value={item.status} /></td><td>{item.vigenciaContratualFinal ?? "—"}</td>
          <td>{item.vigenciaTedFinal ?? "—"}</td></tr>)}
          {!items.length && <tr><td colSpan={7} className="empty">Nenhum processo encontrado.</td></tr>}</tbody></table></div>
    </section>
  </div>;
}

function PasswordChange({ session, onDone }: { session: Session; onDone: () => void }) {
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

function Dashboard({ token }: { token: string }) {
  const [data, setData] = useState<DashboardData>(emptyDashboard);
  useEffect(() => { request<DashboardData>("/api/v1/dashboard", {}, token).then(setData); }, [token]);
  return <><div className="metrics">
    <Metric label="Em formalização" value={data.processosPorStatus.EM_FORMALIZACAO ?? 0} />
    <Metric label="Em vigência" value={data.processosPorStatus.EM_VIGENCIA ?? 0} />
    <Metric label="Concluídos" value={data.processosPorStatus.CONCLUIDO ?? 0} />
    <Metric label="Percentual concluído" value={`${data.percentualConcluidos.toFixed(1)}%`} />
  </div><div className="grid two"><section className="panel"><h2>Vigências</h2>
    <div className="alert-row"><span>Contratual · próximos 120 dias</span><strong>{data.alertasContratuais}</strong></div>
    <div className="alert-row"><span>TED · próximos 120 dias</span><strong>{data.alertasTed}</strong></div>
    <div className="alert-row"><span>Valor total vigente</span><strong>{money(data.valorTotalVigente)}</strong></div>
  </section><section className="panel"><h2>Tramitação</h2>
    <div className="alert-row"><span>Maior gargalo</span><strong>{data.maiorGargalo ?? "Sem dados"}</strong></div>
    <div className="alert-row"><span>Tempo inicial médio</span><strong>{data.tempoMedioTramitacaoInicialDias.toFixed(1)} dias</strong></div>
    {Object.entries(data.permanenciaMediaPorSetor).map(([name, days]) =>
      <div className="bar" key={name}><span>{name}</span><i style={{ width: `${Math.min(100, days * 2)}%` }} /><b>{days.toFixed(1)}d</b></div>)}
  </section></div></>;
}

function Processes({ token, notify }: Props) {
  const [items, setItems] = useState<ProcessoAdministrativo[]>([]);
  const [selected, setSelected] = useState<ProcessoAdministrativo | null>(null);
  const [setores, setSetores] = useState<Setor[]>([]);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const query = new URLSearchParams({ size: "100", ...filters }).toString();
  const load = useCallback(() => Promise.all([
    request<Page<ProcessoAdministrativo>>(`/api/v1/processos?${query}`, {}, token),
    request<Setor[]>("/api/v1/setores", {}, token)
  ]).then(([page, sectors]) => { setItems(page.content); setSetores(sectors); }), [token, query]);
  useEffect(() => { void load(); }, [load]);

  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget; const f = new FormData(form);
    try { await request("/api/v1/processos", { method: "POST", body: JSON.stringify({
      numero: f.get("numero"), origem: f.get("origem"), numeroProjeto: f.get("projeto")
    }) }, token); form.reset(); notify("Processo Administrativo criado."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function move(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (!selected) return; const f = new FormData(e.currentTarget);
    try { await request("/api/v1/movimentacoes", { method: "POST", body: JSON.stringify({
      contextoTipo: "FORMALIZACAO", contextoId: selected.id, dataMovimentacao: f.get("data"),
      setorDestinoId: Number(f.get("setor")), observacao: f.get("observacao")
    }) }, token); notify("Movimentação registrada sem alterar o histórico."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function formalize(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (!selected) return; const f = new FormData(e.currentTarget);
    try { await request(`/api/v1/processos/${selected.id}/instrumento`, { method: "POST", body: JSON.stringify({
      numero: f.get("numero"), tipo: f.get("tipo"), objeto: f.get("objeto"), descricao: f.get("descricao"),
      natureza: f.get("natureza"), coordenador: f.get("coordenador"),
      participes: String(f.get("participes")).split("\n").filter(Boolean),
      valorAtual: Number(f.get("valor")), vigenciaContratualFinal: f.get("contratual"),
      vigenciaTedFinal: f.get("ted") || null, dataFormalizacao: f.get("data"),
      documentoAssinadoId: Number(f.get("documento"))
    }) }, token); notify("Instrumento Contratual formalizado."); setSelected(null); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function edit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (!selected) return; const f = new FormData(e.currentTarget);
    try { const updated = await request<ProcessoAdministrativo>(
      `/api/v1/processos/${selected.id}`, { method: "PUT", body: JSON.stringify({
        origem: f.get("origem"), numeroProjeto: f.get("projeto") || null, responsavelId: null
      }) }, token); setSelected(updated); notify("Processo Administrativo atualizado."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function deactivate() {
    if (!selected) return;
    try { await request(`/api/v1/processos/${selected.id}`, { method: "DELETE" }, token);
      setSelected(null); notify("Processo Administrativo desativado; o registro histórico foi preservado."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  function filter(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setFilters(Object.fromEntries([...new FormData(e.currentTarget).entries()]
      .filter(([, value]) => String(value).trim()).map(([key, value]) => [key, String(value)])));
  }
  return <div className="grid process-layout"><section className="panel"><h2>Novo Processo Administrativo</h2>
    <form className="stack" onSubmit={create}><label>Número<input name="numero" required /></label>
      <label>Origem<input name="origem" required /></label><label>Número do projeto<input name="projeto" /></label>
      <button className="primary">Cadastrar processo</button></form></section>
    <section className="panel span"><h2>Processos Administrativos ativos</h2>
      <form className="inline-form" onSubmit={filter}><label>Filtrar por número<input name="numero" /></label>
        <label>Filtrar por origem<input name="origem" /></label><label>Filtrar por tipo<select name="tipo">
          <option value="">Todos</option>{["CONTRATO_GESTAO", "CONVENIO", "ACORDO_PARCERIA", "ACORDO_COOPERACAO_TECNICA"].map(x =>
            <option key={x}>{x}</option>)}</select></label>
        <label>Filtrar por status<select name="status"><option value="">Todos</option>
          {["EM_FORMALIZACAO", "EM_VIGENCIA", "CONCLUIDO"].map(x => <option key={x}>{x}</option>)}</select></label>
        <label>Filtrar por vigência<select name="vigencia"><option value="">Todas</option>
          {["VALIDA", "PROXIMA_VENCIMENTO", "VENCIDA", "NAO_INFORMADA"].map(x => <option key={x}>{x}</option>)}</select></label>
        <button>Filtrar</button></form><div className="cards">{items.map(p =>
      <button key={p.id} className={`process-card ${selected?.id === p.id ? "selected" : ""}`} onClick={() => setSelected(p)}>
        <div><strong>{p.numero}</strong><small>{p.origem} · {p.numeroProjeto ?? "sem projeto"}</small></div>
        <Badge value={p.status} /><small>Setor atual: {p.setorAtual ?? "sem movimentação"}</small></button>)}</div>
      {!items.length && <p className="empty">Cadastre o primeiro Processo Administrativo.</p>}</section>
    {selected && <section className="panel span"><h2>Tramitação livre · {selected.numero}</h2>
      <form className="inline-form" onSubmit={move}><label>Data<input name="data" type="date"
        defaultValue={new Date().toISOString().slice(0, 10)} required /></label>
        <label>Destino<select name="setor" required><option value="">Selecione</option>{setores.map(s =>
          <option key={s.id} value={s.id}>{s.sigla} · {s.nome}</option>)}</select></label>
        <label>Observação<input name="observacao" /></label><button className="primary">Registrar</button></form></section>}
    {selected && <section className="panel span"><h2>Editar processo · {selected.numero}</h2>
      <form className="inline-form" onSubmit={edit}><label>Origem<input name="origem" defaultValue={selected.origem} required /></label>
        <label>Número do projeto<input name="projeto" defaultValue={selected.numeroProjeto} /></label>
        <button className="primary">Salvar</button><button type="button" onClick={deactivate}>Desativar</button></form></section>}
    {selected && !selected.instrumento && <section className="panel span"><h2>Formalizar Instrumento Contratual</h2>
      <p className="muted">Crie primeiro um Documento Assinado PDF vinculado ao Processo Administrativo.</p>
      <form className="inline-form" onSubmit={formalize}><label>Número<input name="numero" required /></label>
        <label>Tipo<select name="tipo">{["CONTRATO_GESTAO", "CONVENIO", "ACORDO_PARCERIA", "ACORDO_COOPERACAO_TECNICA"].map(x => <option key={x}>{x}</option>)}</select></label>
        <label>Objeto<input name="objeto" required /></label><label>Descrição<input name="descricao" /></label>
        <label>Natureza<input name="natureza" required /></label><label>Coordenador<input name="coordenador" required /></label>
        <label>Partícipes, um por linha<input name="participes" required /></label><label>Valor<input name="valor" type="number" min="0" step=".01" required /></label>
        <label>Vigência contratual<input name="contratual" type="date" required /></label><label>Vigência TED<input name="ted" type="date" /></label>
        <label>Data de formalização<input name="data" type="date" required /></label><label>Documento ID<input name="documento" type="number" required /></label>
        <button className="primary">Formalizar</button></form></section>}
  </div>;
}

function Documents({ token, notify }: Props) {
  const [ownerType, setOwnerType] = useState("PROCESSO");
  const [ownerId, setOwnerId] = useState("");
  const [items, setItems] = useState<Documento[]>([]);
  async function search() {
    try { setItems(await request(`/api/v1/documentos?proprietarioTipo=${ownerType}&proprietarioId=${ownerId}`, {}, token)); }
    catch (e) { notify((e as Error).message); }
  }
  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget); const body = new FormData();
    ["proprietarioTipo", "proprietarioId", "categoria", "titulo"].forEach(k => body.append(k, String(f.get(k))));
    body.append("arquivo", f.get("arquivo")!);
    try { await upload("/api/v1/documentos", body, token); notify("Documento e primeira versão armazenados."); await search(); }
    catch (error) { notify((error as Error).message); }
  }
  async function version(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget); const body = new FormData();
    body.append("arquivo", f.get("arquivo")!);
    try { await upload(`/api/v1/documentos/${f.get("documento")}/versoes`, body, token);
      notify("Nova versão imutável armazenada."); await search(); }
    catch (error) { notify((error as Error).message); }
  }
  async function deactivate(id: number) {
    try { await request(`/api/v1/documentos/${id}`, { method: "DELETE" }, token);
      notify("Documento desativado; as versões históricas foram preservadas."); await search(); }
    catch (error) { notify((error as Error).message); }
  }
  return <div className="grid two"><section className="panel"><h2>Novo documento</h2><form className="stack" onSubmit={create}>
    <label>Proprietário<select name="proprietarioTipo" value={ownerType} onChange={e => setOwnerType(e.target.value)}>
      {["PROCESSO", "INSTRUMENTO", "TERMO_ADITIVO", "APOSTILAMENTO"].map(x => <option key={x}>{x}</option>)}</select></label>
    <label>ID do proprietário<input name="proprietarioId" value={ownerId} onChange={e => setOwnerId(e.target.value)} required /></label>
    <label>Categoria<select name="categoria"><option>ADMINISTRATIVO</option><option>ASSINADO</option></select></label>
    <label>Título<input name="titulo" required /></label><label>Arquivo<input name="arquivo" type="file" required /></label>
    <button className="primary">Armazenar versão 1</button></form><hr /><h2>Nova versão</h2>
    <form className="stack" onSubmit={version}><label>Documento ID<input name="documento" type="number" required /></label>
      <label>Arquivo<input name="arquivo" type="file" required /></label><button className="primary">Adicionar versão</button></form></section>
    <section className="panel"><div className="panel-title"><h2>Documentos ativos</h2><button onClick={search}>Atualizar</button></div>
      {items.map(d => <article className="doc" key={d.id}><div><strong>#{d.id} · {d.titulo}</strong>
        <small>{d.categoria} · {d.versoes.length} versão(ões)</small></div>
        <button onClick={() => download(`/api/v1/documentos/${d.id}/versoes/${d.versoes[0]?.versao}/arquivo`, token)}>Baixar atual</button>
        <button onClick={() => deactivate(d.id)}>Desativar</button></article>)}</section></div>;
}

function Alterations({ token, notify }: Props) {
  const [operation, setOperation] = useState("ORIGINAL");
  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget; const f = new FormData(form);
    try { const result = await request<{ id: number }>("/api/v1/alteracoes", { method: "POST", body: JSON.stringify({
      instrumentoId: Number(f.get("instrumento")), tipo: f.get("tipo"), numeroOficial: f.get("numero"),
      operacao: operation, referenciaId: operation === "ORIGINAL" ? null : Number(f.get("referencia")),
      alteracoes: operation === "CANCELAMENTO" ? {} : { [String(f.get("campo"))]: f.get("valor") }
    }) }, token); notify(`Rascunho #${result.id} criado. Vincule o PDF assinado e efetive abaixo.`);
      form.reset(); setOperation("ORIGINAL"); }
    catch (error) { notify((error as Error).message); }
  }
  async function effect(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    try { await request(`/api/v1/alteracoes/${f.get("alteracao")}/efetivacao`, { method: "POST", body: JSON.stringify({
      dataEfetivacao: f.get("data"), ordemOficial: Number(f.get("ordem")),
      documentoAssinadoId: Number(f.get("documento"))
    }) }, token); notify("Alteração efetivada e estado atual recomputado."); }
    catch (error) { notify((error as Error).message); }
  }
  return <section className="panel"><h2>Novo rascunho, retificação ou cancelamento</h2><p className="muted">Termos alteram condições contratuais;
    apostilamentos ficam limitados aos campos não contratuais aprovados.</p><form className="inline-form" onSubmit={create}>
    <label>Instrumento ID<input name="instrumento" type="number" required /></label><label>Tipo<select name="tipo">
      <option>TERMO_ADITIVO</option><option>APOSTILAMENTO</option></select></label><label>Número oficial<input name="numero" required /></label>
    <label>Operação<select name="operacao" value={operation} onChange={e => setOperation(e.target.value)}>
      <option>ORIGINAL</option><option>RETIFICACAO</option><option>CANCELAMENTO</option></select></label>
    {operation !== "ORIGINAL" && <label>Alteração de referência<input name="referencia" type="number" required /></label>}
    {operation !== "CANCELAMENTO" && <><label>Campo<select name="campo">{["OBJETO", "DESCRICAO", "NATUREZA", "COORDENADOR", "PARTICIPES",
      "VALOR_ATUAL", "VIGENCIA_CONTRATUAL_FINAL", "VIGENCIA_TED_FINAL"].map(x => <option key={x}>{x}</option>)}</select></label>
      <label>Novo valor<input name="valor" required /></label></>}
    <button className="primary">Criar rascunho</button></form>
    <hr /><h2>Efetivar alteração</h2><p className="muted">Anexe antes um PDF assinado ao Termo Aditivo ou Apostilamento.</p>
    <form className="inline-form" onSubmit={effect}><label>Alteração ID<input name="alteracao" type="number" required /></label>
      <label>Data<input name="data" type="date" required /></label><label>Ordem oficial<input name="ordem" type="number" min="1" required /></label>
      <label>Documento ID<input name="documento" type="number" required /></label><button className="primary">Efetivar</button></form></section>;
}

function Reports({ token, notify }: Props) {
  const [items, setItems] = useState<{ id: number; tipo: string; formato: string; criadoEm: string }[]>([]);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const load = useCallback(() => request<typeof items>("/api/v1/relatorios", {}, token).then(setItems), [token]);
  useEffect(() => { void load(); }, [load]);
  async function generate(type: string, format: string) {
    try { await request("/api/v1/relatorios", { method: "POST", body: JSON.stringify({
      tipo: type, formato: format,
      filtros: Object.fromEntries(Object.entries(filters).filter(([, value]) => value))
    }) }, token); notify("Relatório gerado e retido para download."); await load(); }
    catch (e) { notify((e as Error).message); }
  }
  return <section className="panel"><h2>Filtros dos relatórios</h2>
    <div className="inline-form">{["numero", "origem", "ano", "dataInicial", "dataFinal"].map(name =>
      <label key={name}>{name}<input type={name.startsWith("data") ? "date" : "text"} value={filters[name] ?? ""}
        onChange={e => setFilters({ ...filters, [name]: e.target.value })} /></label>)}
      <label>Tipo<select value={filters.tipo ?? ""} onChange={e => setFilters({ ...filters, tipo: e.target.value })}>
        <option value="">Todos</option>{["CONTRATO_GESTAO", "CONVENIO", "ACORDO_PARCERIA", "ACORDO_COOPERACAO_TECNICA"].map(x =>
          <option key={x}>{x}</option>)}</select></label>
      <label>Status<select value={filters.status ?? ""} onChange={e => setFilters({ ...filters, status: e.target.value })}>
        <option value="">Todos</option>{["EM_FORMALIZACAO", "EM_VIGENCIA", "CONCLUIDO"].map(x => <option key={x}>{x}</option>)}</select></label>
      <label>Vigência<select value={filters.vigencia ?? ""} onChange={e => setFilters({ ...filters, vigencia: e.target.value })}>
        <option value="">Todas</option>{["VALIDA", "PROXIMA_VENCIMENTO", "VENCIDA", "NAO_INFORMADA"].map(x =>
          <option key={x}>{x}</option>)}</select></label></div>
    <h2>Gerar relatório</h2><div className="report-actions">
    {["ANUAL_PROCESSOS", "INSTRUMENTOS_POR_TIPO", "HISTORICO_TRAMITACOES", "VIGENCIAS", "CONSOLIDADO"].map(type =>
      <div key={type}><strong>{type.replaceAll("_", " ")}</strong>{["PDF", "XLSX", "CSV"].map(format =>
        <button key={format} onClick={() => generate(type, format)}>{format}</button>)}</div>)}</div>
    <h2>Histórico</h2>{items.map(item => <article className="doc" key={item.id}><div><strong>{item.tipo.replaceAll("_", " ")}</strong>
      <small>{item.formato} · {new Date(item.criadoEm).toLocaleString("pt-BR")}</small></div>
      <button onClick={() => download(`/api/v1/relatorios/${item.id}/arquivo`, token)}>Baixar</button></article>)}</section>;
}

function Notifications({ token }: { token: string }) {
  const [items, setItems] = useState<{ id: number; mensagem: string; tipo: string; lida: boolean; criadaEm: string }[]>([]);
  const load = useCallback(() => request<typeof items>("/api/v1/notificacoes", {}, token).then(setItems), [token]);
  useEffect(() => { void load(); }, [load]);
  async function read(id: number) { await request(`/api/v1/notificacoes/${id}/lida`, { method: "PATCH" }, token); await load(); }
  return <section className="panel"><h2>Caixa de entrada</h2>{items.map(n => <button className={`notification ${n.lida ? "read" : ""}`}
    key={n.id} onClick={() => read(n.id)}><span>{n.lida ? "✓" : "•"}</span><div><strong>{n.tipo.replaceAll("_", " ")}</strong>
      <p>{n.mensagem}</p><small>{new Date(n.criadaEm).toLocaleString("pt-BR")}</small></div></button>)}
    {!items.length && <p className="empty">Nenhuma notificação.</p>}</section>;
}

function Administration({ token, notify }: Props) {
  type UsuarioAdmin = {
    id: number; nome: string; email: string; login: string; perfil: string;
    ativo: boolean; senhaTemporaria: boolean;
  };
  const [users, setUsers] = useState<UsuarioAdmin[]>([]);
  const [selectedUser, setSelectedUser] = useState<UsuarioAdmin | null>(null);
  const [sectors, setSectors] = useState<Setor[]>([]);
  const load = useCallback(() => Promise.all([
    request<typeof users>("/api/v1/admin/usuarios", {}, token),
    request<Setor[]>("/api/v1/admin/setores", {}, token)
  ]).then(([u, s]) => { setUsers(u); setSectors(s); }), [token]);
  useEffect(() => { void load(); }, [load]);
  async function user(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    try { await request("/api/v1/admin/usuarios", { method: "POST", body: JSON.stringify({
      nome: f.get("nome"), email: f.get("email"), login: f.get("login"),
      senhaTemporaria: f.get("senha"), perfil: f.get("perfil")
    }) }, token); notify("Usuário criado com senha temporária."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function sector(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    try { await request("/api/v1/admin/setores", { method: "POST", body: JSON.stringify({
      sigla: f.get("sigla"), nome: f.get("nome")
    }) }, token); notify("Setor incluído no catálogo."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function resetPassword(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    try { await request(`/api/v1/admin/usuarios/${f.get("usuario")}/senha`, {
      method: "PATCH", body: JSON.stringify({ novaSenhaTemporaria: f.get("senha") })
    }, token); notify("Senha temporária redefinida; a troca será exigida no próximo acesso."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function toggleUser(id: number, ativo: boolean) {
    try { await request(`/api/v1/admin/usuarios/${id}/ativo?ativo=${!ativo}`, { method: "PATCH" }, token);
      notify(`Usuário ${ativo ? "desativado" : "reativado"}.`); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function changeProfile(id: number, perfil: string) {
    try { await request(`/api/v1/admin/usuarios/${id}/perfil?perfil=${perfil}`, { method: "PATCH" }, token);
      notify("Perfil de acesso atualizado."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function detailUser(id: number) {
    try {
      setSelectedUser(await request<UsuarioAdmin>(`/api/v1/admin/usuarios/${id}`, {}, token));
    } catch (error) { notify((error as Error).message); }
  }
  async function toggleSector(id: number, ativo: boolean) {
    try { await request(`/api/v1/admin/setores/${id}/ativo?ativo=${!ativo}`, { method: "PATCH" }, token);
      notify(`Setor ${ativo ? "desativado" : "reativado"}.`); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  return <div className="grid two"><section className="panel"><h2>Novo usuário DIPAC</h2><form className="stack" onSubmit={user}>
    <label>Nome<input name="nome" required /></label><label>E-mail<input name="email" type="email" required /></label>
    <label>Login imutável<input name="login" required /></label><label>Senha temporária<input name="senha" type="password" required /></label>
    <label>Perfil<select name="perfil"><option>OPERADOR_DIPAC</option><option>ADMINISTRADOR_DIPAC</option></select></label>
    <button className="primary">Criar usuário</button></form><hr /><h2>Redefinir senha temporária</h2>
    <form className="inline-form" onSubmit={resetPassword}><label>Usuário ID<input name="usuario" type="number" required /></label>
      <label>Nova senha temporária<input name="senha" type="password" required /></label>
      <button>Redefinir</button></form>{users.map(u => <article className="doc user-row" key={u.id}>
      <div><strong>{u.nome}</strong><small>@{u.login}</small></div><Badge value={u.ativo ? "ATIVO" : "INATIVO"} />
      <label>Perfil<select value={u.perfil} onChange={e => changeProfile(u.id, e.target.value)}>
        <option>OPERADOR_DIPAC</option><option>ADMINISTRADOR_DIPAC</option></select></label>
      <button aria-label={`Ver detalhes de ${u.nome}`} onClick={() => detailUser(u.id)}>Detalhes</button>
      <button onClick={() => toggleUser(u.id, u.ativo)}>{u.ativo ? "Desativar" : "Reativar"}</button></article>)}
      {selectedUser && <article className="user-detail">
        <div><h3>Detalhes do Usuário Interno</h3><button aria-label="Fechar detalhes" onClick={() => setSelectedUser(null)}>×</button></div>
        <strong>{selectedUser.nome}</strong><span>{selectedUser.email}</span><span>@{selectedUser.login}</span>
        <Badge value={selectedUser.perfil} /><Badge value={selectedUser.ativo ? "ATIVO" : "INATIVO"} />
        {selectedUser.senhaTemporaria && <Badge value="TROCA_DE_SENHA_OBRIGATÓRIA" />}
      </article>}</section>
    <section className="panel"><h2>Catálogo de setores</h2><form className="inline-form" onSubmit={sector}>
      <label>Sigla<input name="sigla" required /></label><label>Nome<input name="nome" required /></label>
      <button className="primary">Adicionar</button></form>{sectors.map(s => <article className="doc" key={s.id}>
        <div><strong>{s.sigla}</strong><small>{s.nome}</small></div><Badge value={s.ativo ? "ATIVO" : "INATIVO"} />
        <button onClick={() => toggleSector(s.id, s.ativo)}>{s.ativo ? "Desativar" : "Reativar"}</button></article>)}</section></div>;
}

function Badge({ value }: { value: string }) { return <span className={`badge ${value.toLowerCase()}`}>{value.replaceAll("_", " ")}</span>; }
function Metric({ label, value }: { label: string; value: string | number }) {
  return <article className="metric"><small>{label}</small><strong>{value}</strong></article>;
}
function money(value: number) { return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value); }
type Props = { token: string; notify: (message: string) => void };
