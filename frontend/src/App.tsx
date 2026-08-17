import { FormEvent, useCallback, useEffect, useState } from "react";
import { download, request, Session, upload } from "./api";

type Page<T> = {
  content: T[]; totalElements: number; totalPages: number; number: number; size: number;
};
type StatusProcesso = "EM_FORMALIZACAO" | "EM_VIGENCIA" | "CONCLUIDO";
type ProcessoAdministrativo = {
  id: number; numero: string; origem: string; numeroProjeto?: string; status: string;
  ativo: boolean; responsavel?: ResponsavelProcesso; setorAtual?: string; instrumento?: Instrumento;
};
type TipoInstrumento = "CONTRATO_GESTAO" | "CONVENIO" | "ACORDO_PARCERIA" | "ACORDO_COOPERACAO_TECNICA";
type SituacaoVigencia = "VALIDA" | "PROXIMA_VENCIMENTO" | "VENCIDA" | "NAO_INFORMADA";
type Instrumento = {
  id: number; numero: string; tipo: TipoInstrumento; coordenador: string; valorAtual: number;
  objeto?: string; descricao?: string; natureza?: string; participes?: string[];
  vigenciaContratualFinal: string; vigenciaTedFinal?: string; documentoAssinadoId: number;
  situacaoContratual: SituacaoVigencia; situacaoTed: SituacaoVigencia;
};
type Setor = { id: number; sigla: string; nome: string; ativo: boolean };
type Movimentacao = {
  id: number; dataMovimentacao: string; sequenciaDiaria: number; setorDestino: Setor;
  autor: { id: number; login: string; nome: string }; observacao?: string; inseridoEm: string;
};
type PermanenciaSetor = {
  setor: Setor; dataChegada: string; dataSaida?: string; diasCorridos: number; aberta: boolean;
};
type HistoricoTramitacao = {
  setorAtual?: Setor | null; movimentacoes: Movimentacao[]; permanencias: PermanenciaSetor[];
};
const rotulosCampo = {
  OBJETO: "Objeto", DESCRICAO: "Descrição", NATUREZA: "Natureza", COORDENADOR: "Coordenador",
  PARTICIPES: "Partícipes", VALOR_ATUAL: "Valor atual", VIGENCIA_CONTRATUAL_FINAL: "Vigência contratual final",
  VIGENCIA_TED_FINAL: "Vigência TED final"
} as const;
type CampoInstrumento = keyof typeof rotulosCampo;
type MudancaAlteracao = { campo: CampoInstrumento; valorAnterior?: string | null; valorNovo?: string | null };
type EstadoAtualInstrumento = {
  objeto: string; descricao?: string | null; natureza: string; coordenador: string; participes: string[];
  valorAtual: number; vigenciaContratualFinal: string; vigenciaTedFinal?: string | null; statusProcesso: StatusProcesso;
  precedenciaPorCampo?: Partial<Record<CampoInstrumento, { dataEfetivacao: string; ordemOficial: number }>>;
};
type AlteracaoVinculada = {
  id: number; numeroOficial: string; tipo: "TERMO_ADITIVO" | "APOSTILAMENTO";
  estado: "RASCUNHO" | "EFETIVADA"; operacao: "ORIGINAL" | "RETIFICACAO" | "CANCELAMENTO";
  referenciaId?: number | null; dataEfetivacao?: string | null; ordemOficial?: number | null;
  produzEfeitoAtual: boolean; valoresProduzidos: Partial<Record<CampoInstrumento, string | null>>;
};
type AlteracaoContratual = {
  id: number; instrumentoId: number; tipo: "TERMO_ADITIVO" | "APOSTILAMENTO";
  estado: "RASCUNHO" | "EFETIVADA" | "CANCELADA"; numeroOficial: string;
  dataEfetivacao?: string | null; ordemOficial?: number | null;
  operacao: "ORIGINAL" | "RETIFICACAO" | "CANCELAMENTO"; referenciaId?: number | null;
  documentoAssinadoId?: number | null; mudancas: MudancaAlteracao[];
  estadoAtualInstrumento: EstadoAtualInstrumento; tramitacao: HistoricoTramitacao;
  cadeia?: AlteracaoVinculada[];
};
type ResponsavelProcesso = {
  id: number; nome: string; perfil: "ADMINISTRADOR_DIPAC" | "OPERADOR_DIPAC";
};
type AutorDocumento = { id: number; nome: string };
type VersaoDocumento = {
  versao: number; nomeArquivo: string; tipoMime: string; tamanho: number;
  checksumSha256: string; criadoPor: AutorDocumento; criadoEm: string;
};
type Documento = {
  id: number; titulo: string; categoria: string; ativo: boolean;
  criadoPor: AutorDocumento; criadoEm: string; versoes: VersaoDocumento[];
};
type Publico = {
  numeroProcesso: string; tipoInstrumento: string; origem: string; coordenador: string;
  status: string; vigenciaContratualFinal?: string; vigenciaTedFinal?: string;
};
type RegistroAuditoria = {
  id: number;
  acao: string;
  resultado: "SUCESSO" | "FALHA";
  ator: { id: number; login: string; nome: string } | null;
  objeto: { tipo: string; id: number | null };
  detalhes?: string;
  ipOrigem?: string;
  criadoEm: string;
};
type NotificacaoInterna = {
  id: number;
  mensagem: string;
  tipo: "CHEGADA_TRAMITACAO" | "ALERTA_VIGENCIA_CONTRATUAL" | "ALERTA_VIGENCIA_TED";
  processoId: number | null;
  lida: boolean;
  criadaEm: string;
};

type DashboardData = {
  processosPorStatus: Record<string, number>; percentualConcluidos: number;
  alertasContratuais: number; alertasTed: number; valorTotalVigente: number;
  instrumentosPorTipo: Record<string, number>; permanenciaMediaPorSetor: Record<string, number>;
  maiorGargalo: string | null;
  detalhesPermanenciaPorSetor: Record<string, {
    processoId: number; numeroProcesso: string; dataChegada: string;
    dataSaida?: string | null; diasCorridos: number; aberta: boolean;
  }[]>;
  tempoMedioTramitacaoInicialDias: number;
  detalhesTempoTramitacaoInicial: {
    processoId: number; numeroProcesso: string; dataCadastro: string;
    dataFormalizacao?: string | null; diasCorridos: number; aberta: boolean;
  }[];
  formalizacoesMensais: Record<string, number>; conclusoesMensais: Record<string, number>;
};

export default function App() {
  const [session, setSession] = useState<Session | null>(() => {
    const saved = localStorage.getItem("sicc-session");
    return saved ? JSON.parse(saved) : null;
  });
  const [tab, setTab] = useState("dashboard");
  const [notificacaoEmFoco, setNotificacaoEmFoco] = useState<number | null>(null);
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
            <>
              <button className={tab === "administracao" ? "active" : ""} onClick={() => setTab("administracao")}>
                Administração
              </button>
              <button className={tab === "auditoria" ? "active" : ""} onClick={() => setTab("auditoria")}>
                Registros de Auditoria
              </button>
            </>}
          <button onClick={() => saveSession(null)}>Sair</button>
        </nav>
        <div className="profile"><span>{session.perfil === "ADMINISTRADOR_DIPAC" ? "AD" : "OP"}</span>
          <small>{session.perfil.replaceAll("_", " ")}</small></div>
      </aside>
      <main>
        <header><div><small>Sistema Integrado de Controle de Contratos</small><h1>{labels[tab]}</h1></div></header>
        {message && <div className="toast" onClick={() => setMessage("")}>{message}</div>}
        {tab === "dashboard" && <Dashboard token={session.token} />}
        {tab === "processos" && <Processes token={session.token} notify={setMessage}
          notificacaoEmFoco={notificacaoEmFoco}
          onNotificacaoFocada={() => setNotificacaoEmFoco(null)} />}
        {tab === "documentos" && <Documents token={session.token} notify={setMessage} />}
        {tab === "alteracoes" && <Alterations token={session.token} notify={setMessage} />}
        {tab === "relatorios" && <Reports token={session.token} notify={setMessage} />}
        {tab === "notificacoes" && <Notifications token={session.token}
          onVerProcessoAdministrativo={notificacaoId => {
          setNotificacaoEmFoco(notificacaoId);
          setTab("processos");
        }} />}
        {tab === "administracao" && <Administration token={session.token} notify={setMessage} />}
        {tab === "auditoria" && <AuditRecords token={session.token} />}
      </main>
    </div>
  );
}

const labels: Record<string, string> = {
  dashboard: "Visão geral", processos: "Processos Administrativos", documentos: "Documentos",
  alteracoes: "Alterações contratuais", relatorios: "Relatórios", notificacoes: "Notificações",
  administracao: "Administração", auditoria: "Registros de Auditoria"
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
      {Object.keys(tipoInstrumentoLabels).map(tipo => <option key={tipo}>{tipo}</option>)}</select></label>
    <label>Status do processo<select name="status"><option value="">Todos</option>
      {(["EM_FORMALIZACAO", "EM_VIGENCIA", "CONCLUIDO"] as StatusProcesso[])
        .map(status => <option key={status}>{status}</option>)}</select></label>
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
      {Object.entries(tipoInstrumentoLabels).map(([tipo, label]) =>
        <div className="alert-row" key={tipo}><span>{label}</span>
          <strong>{data.instrumentosPorTipo[tipo] ?? 0}</strong></div>)}
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

const tipoInstrumentoLabels: Record<TipoInstrumento, string> = {
  CONTRATO_GESTAO: "Contrato de gestão",
  CONVENIO: "Convênio",
  ACORDO_PARCERIA: "Acordo de parceria",
  ACORDO_COOPERACAO_TECNICA: "Acordo de cooperação técnica"
};

function formatarMes(mes: string) {
  return new Intl.DateTimeFormat("pt-BR", { month: "short", year: "numeric", timeZone: "UTC" })
    .format(new Date(`${mes}-01T00:00:00Z`));
}

function formatarNumeroDias(dias: number) {
  return new Intl.NumberFormat("pt-BR", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1
  }).format(dias);
}

function responsavelSelecionado(form: FormData) {
  const valor = form.get("responsavel");
  return valor ? Number(valor) : null;
}

function dataLocalAtual() {
  const agora = new Date();
  const local = new Date(agora.getTime() - agora.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

function formatarDataNegocio(data: string) {
  return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" })
    .format(new Date(`${data}T00:00:00Z`));
}

function formatarMomentoInsercao(instante: string) {
  return instante.replace("T", " ").slice(0, 16);
}

function Processes({ token, notify, notificacaoEmFoco, onNotificacaoFocada }: Props & {
  notificacaoEmFoco: number | null;
  onNotificacaoFocada: () => void;
}) {
  const [items, setItems] = useState<ProcessoAdministrativo[]>([]);
  const [selected, setSelected] = useState<ProcessoAdministrativo | null>(null);
  const [setores, setSetores] = useState<Setor[]>([]);
  const [responsaveis, setResponsaveis] = useState<ResponsavelProcesso[]>([]);
  const [historico, setHistorico] = useState<HistoricoTramitacao | null>(null);
  const [documentosAssinados, setDocumentosAssinados] = useState<Documento[]>([]);
  const [carregandoHistorico, setCarregandoHistorico] = useState(false);
  const [erroHistorico, setErroHistorico] = useState("");
  const [filters, setFilters] = useState<Record<string, string>>({});
  const query = new URLSearchParams({ size: "100", ...filters }).toString();
  const load = useCallback(() => Promise.all([
    request<Page<ProcessoAdministrativo>>(`/api/v1/processos?${query}`, {}, token),
    request<Setor[]>("/api/v1/setores", {}, token),
    request<ResponsavelProcesso[]>("/api/v1/processos/responsaveis", {}, token)
  ]).then(([page, sectors, responsaveisAtivos]) => {
    setItems(page.content);
    setSetores(sectors);
    setResponsaveis(responsaveisAtivos);
  }), [token, query]);
  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (notificacaoEmFoco === null) return;
    let ativo = true;
    request<ProcessoAdministrativo>(
      `/api/v1/notificacoes/${notificacaoEmFoco}/processo`, {}, token
    )
      .then(processo => {
        if (ativo) setSelected(processo);
      })
      .catch(error => {
        if (ativo) notify((error as Error).message);
      })
      .finally(() => {
        if (ativo) onNotificacaoFocada();
      });
    return () => { ativo = false; };
  }, [notificacaoEmFoco, notify, onNotificacaoFocada, token]);
  const loadHistorico = useCallback(async (processoId: number) => {
    setCarregandoHistorico(true);
    setErroHistorico("");
    try {
      setHistorico(await request<HistoricoTramitacao>(
        `/api/v1/processos/${processoId}/tramitacao`, {}, token
      ));
    } catch (error) {
      setHistorico(null);
      setErroHistorico((error as Error).message);
    } finally {
      setCarregandoHistorico(false);
    }
  }, [token]);
  useEffect(() => {
    if (!selected || !selected.ativo) {
      setHistorico(null);
      setErroHistorico("");
      return;
    }
    void loadHistorico(selected.id);
  }, [selected, loadHistorico]);
  useEffect(() => {
    setDocumentosAssinados([]);
    if (!selected?.ativo || selected.instrumento) {
      return;
    }
    let ativo = true;
    request<Documento[]>(
      `/api/v1/documentos?proprietarioTipo=PROCESSO&proprietarioId=${selected.id}`, {}, token
    ).then(documentos => {
      if (!ativo) return;
      setDocumentosAssinados(documentos.filter(documento =>
        documento.ativo && documento.categoria === "ASSINADO"
        && documento.versoes[0]?.tipoMime === "application/pdf"));
    }).catch(error => {
      if (ativo) notify((error as Error).message);
    });
    return () => { ativo = false; };
  }, [notify, selected?.ativo, selected?.id, selected?.instrumento, token]);

  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget; const f = new FormData(form);
    try { await request("/api/v1/processos", { method: "POST", body: JSON.stringify({
      numero: f.get("numero"), origem: f.get("origem"), numeroProjeto: f.get("projeto"),
      responsavelId: responsavelSelecionado(f)
    }) }, token); form.reset(); notify("Processo Administrativo criado."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function move(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (!selected) return; const f = new FormData(e.currentTarget);
    try { await request("/api/v1/movimentacoes", { method: "POST", body: JSON.stringify({
      contextoTipo: "FORMALIZACAO", contextoId: selected.id, dataMovimentacao: f.get("data"),
      setorDestinoId: Number(f.get("setor")), observacao: f.get("observacao")
    }) }, token); notify("Movimentação registrada sem alterar o histórico.");
      await Promise.all([load(), loadHistorico(selected.id)]); }
    catch (error) { notify((error as Error).message); }
  }
  async function formalize(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (!selected) return; const f = new FormData(e.currentTarget);
    try { await request<Instrumento>(`/api/v1/processos/${selected.id}/instrumento`, {
      method: "POST", body: JSON.stringify({
      numero: f.get("numero"), tipo: f.get("tipo"), objeto: f.get("objeto"), descricao: f.get("descricao"),
      natureza: f.get("natureza"), coordenador: f.get("coordenador"),
      participes: String(f.get("participes")).split("\n").map(item => item.trim()).filter(Boolean),
      valorAtual: Number(f.get("valor")), vigenciaContratualFinal: f.get("contratual"),
      vigenciaTedFinal: f.get("ted") || null, dataFormalizacao: f.get("data"),
      documentoAssinadoId: Number(f.get("documento"))
    }) }, token);
      const atualizado = await request<ProcessoAdministrativo>(
        `/api/v1/processos/${selected.id}`, {}, token);
      setSelected(atualizado);
      notify("Instrumento Contratual formalizado.");
      await load();
    }
    catch (error) { notify((error as Error).message); }
  }
  async function edit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (!selected) return; const f = new FormData(e.currentTarget);
    try { const updated = await request<ProcessoAdministrativo>(
      `/api/v1/processos/${selected.id}`, { method: "PUT", body: JSON.stringify({
        origem: f.get("origem"), numeroProjeto: f.get("projeto") || null,
        responsavelId: responsavelSelecionado(f)
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
      <label>Responsável DIPAC<select name="responsavel"><option value="">Sem responsável</option>
        {responsaveis.map(responsavel => <option key={responsavel.id} value={responsavel.id}>
          {responsavel.nome} · {responsavel.perfil.replaceAll("_", " ")}
        </option>)}</select></label>
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
        <Badge value={p.status} /><small>Responsável: {p.responsavel?.nome ?? "não atribuído"}</small>
        <small>Setor atual: {p.setorAtual ?? "sem movimentação"}</small></button>)}</div>
      {!items.length && <p className="empty">Cadastre o primeiro Processo Administrativo.</p>}</section>
    {selected && !selected.ativo && <section className="panel span">
      <h2>Processo Administrativo · {selected.numero}</h2>
      <p className="muted">Registro desativado preservado para consulta histórica.</p>
      <p><strong>Origem:</strong> {selected.origem}</p>
      <p><strong>Status:</strong> {selected.status}</p>
      <p><strong>Setor atual:</strong> {selected.setorAtual ?? "sem movimentação"}</p>
    </section>}
    {selected?.ativo && <section className="panel span tramitacao-panel"><h2>Tramitação livre · {selected.numero}</h2>
      <p className="muted">Cada movimentação é imutável. Para corrigir uma informação, registre uma nova movimentação explicativa.</p>
      <form className="inline-form" onSubmit={move}><label>Data<input name="data" type="date"
        defaultValue={dataLocalAtual()} max={dataLocalAtual()} required /></label>
        <label>Destino<select name="setor" required><option value="">Selecione</option>{setores.map(s =>
          <option key={s.id} value={s.id}>{s.sigla} · {s.nome}</option>)}</select></label>
        <label>Observação<input name="observacao" required /></label><button className="primary">Registrar</button></form>
      {carregandoHistorico && <p className="history-state" role="status">Carregando tramitação…</p>}
      {erroHistorico && <p className="history-state error" role="alert">{erroHistorico}</p>}
      {!carregandoHistorico && !erroHistorico && historico && <>
        <div className="tramitacao-metrics">
          <div><small>Setor atual</small><strong>{historico.setorAtual?.sigla ?? "Sem movimentação"}</strong></div>
          <div><small>Permanência atual</small><strong>{historico.permanencias.at(-1)?.aberta
            ? `${historico.permanencias.at(-1)?.diasCorridos} dias no setor atual`
            : "Sem período aberto"}</strong></div>
        </div>
        <div className="timeline-heading"><h3>Linha do tempo</h3>
          <small>Ordem por data de negócio e sequência diária</small></div>
        {historico.movimentacoes.length ? <ol className="timeline">{historico.movimentacoes.map(movimento =>
          <li key={movimento.id}><div className="timeline-marker" />
            <div><time>{formatarDataNegocio(movimento.dataMovimentacao)} · sequência {movimento.sequenciaDiaria}</time>
              <strong>{movimento.setorDestino.sigla} · {movimento.setorDestino.nome}</strong>
              <p>{movimento.observacao ?? "Sem observação"}</p>
              <small>Registrado por {movimento.autor.nome} em {formatarMomentoInsercao(movimento.inseridoEm)}</small>
            </div></li>)}</ol> : <p className="empty">Nenhuma movimentação registrada.</p>}
        {historico.permanencias.length > 0 && <><h3 className="permanencia-title">Permanência no Setor por passagem</h3>
          <div className="permanencias">{historico.permanencias.map((permanencia, indice) =>
            <div key={`${permanencia.setor.id}-${permanencia.dataChegada}-${indice}`}>
              <strong>{permanencia.setor.sigla}</strong>
              <span>{permanencia.diasCorridos} dias corridos</span>
              <small>{formatarDataNegocio(permanencia.dataChegada)} até {permanencia.aberta
                ? "hoje" : formatarDataNegocio(permanencia.dataSaida!)}</small>
            </div>)}</div></>}
      </>}
    </section>}
    {selected?.ativo && <section className="panel span"><h2>Editar Processo Administrativo · {selected.numero}</h2>
      <form className="inline-form" onSubmit={edit}><label>Origem<input name="origem" defaultValue={selected.origem} required /></label>
        <label>Número do projeto<input name="projeto" defaultValue={selected.numeroProjeto} /></label>
        <label>Responsável DIPAC<select name="responsavel" defaultValue={selected.responsavel?.id ?? ""}>
          <option value="">Sem responsável</option>{responsaveis.map(responsavel =>
            <option key={responsavel.id} value={responsavel.id}>{responsavel.nome}</option>)}
        </select></label>
        <button className="primary">Salvar</button><button type="button" onClick={deactivate}>Desativar</button></form></section>}
    {selected?.ativo && !selected.instrumento && <section className="panel span"><h2>Formalizar Instrumento Contratual</h2>
      <p className="muted">Crie primeiro um Documento Assinado PDF vinculado ao Processo Administrativo.</p>
      <form className="inline-form" onSubmit={formalize}><label>Número<input name="numero" required /></label>
        <label>Tipo<select name="tipo">{["CONTRATO_GESTAO", "CONVENIO", "ACORDO_PARCERIA", "ACORDO_COOPERACAO_TECNICA"].map(x => <option key={x}>{x}</option>)}</select></label>
        <label>Objeto<input name="objeto" required /></label><label>Descrição<input name="descricao" /></label>
        <label>Natureza<input name="natureza" required /></label><label>Coordenador<input name="coordenador" required /></label>
        <label>Partícipes, um por linha<textarea name="participes" rows={3} required /></label><label>Valor<input name="valor" type="number" min="0" step=".01" required /></label>
        <label>Vigência contratual<input name="contratual" type="date" required /></label><label>Vigência TED<input name="ted" type="date" /></label>
        <label>Data de formalização<input name="data" type="date" max={dataLocalAtual()} required /></label>
        <label>Documento assinado PDF<select name="documento" required><option value="">Selecione</option>
          {documentosAssinados.map(documento => <option key={documento.id} value={documento.id}>
            #{documento.id} · {documento.titulo} · versão {documento.versoes[0]?.versao}
          </option>)}</select></label>
        <button className="primary">Formalizar</button></form></section>}
    {selected?.ativo && selected.instrumento && <section className="panel span instrumento-resumo">
      <h2>Instrumento Contratual · {selected.instrumento.numero}</h2>
      <div className="tramitacao-metrics">
        <div><small>Tipo</small><strong>{selected.instrumento.tipo.replaceAll("_", " ")}</strong></div>
        <div><small>Coordenador</small><strong>{selected.instrumento.coordenador}</strong></div>
        <div><small>Vigência contratual final</small><strong>{selected.instrumento.vigenciaContratualFinal}</strong></div>
        <div><small>Vigência TED final</small><strong>{selected.instrumento.vigenciaTedFinal ?? "Não informada"}</strong></div>
      </div>
      <p className="muted">Documento assinado #{selected.instrumento.documentoAssinadoId}</p>
    </section>}
  </div>;
}

function Documents({ token, notify }: Props) {
  const [ownerType, setOwnerType] = useState("PROCESSO");
  const [ownerId, setOwnerId] = useState("");
  const [category, setCategory] = useState("ADMINISTRATIVO");
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
      {["PROCESSO", "INSTRUMENTO", "TERMO_ADITIVO", "APOSTILAMENTO"].map(x =>
        <option key={x} disabled={category === "ADMINISTRATIVO" && x !== "PROCESSO"}>{x}</option>)}</select>
      {category === "ADMINISTRATIVO" && <small>Documentos administrativos pertencem ao Processo.</small>}</label>
    <label>ID do proprietário<input name="proprietarioId" value={ownerId} onChange={e => setOwnerId(e.target.value)} required /></label>
    <label>Categoria<select name="categoria" value={category} onChange={e => {
      setCategory(e.target.value);
      if (e.target.value === "ADMINISTRATIVO") setOwnerType("PROCESSO");
    }}><option>ADMINISTRATIVO</option><option>ASSINADO</option></select></label>
    <label>Título<input name="titulo" required /></label><label>Arquivo<input name="arquivo" type="file"
      accept=".pdf,.docx,.xlsx,.csv" required /><small>PDF, DOCX, XLSX ou CSV · máximo de 20 MB</small></label>
    <button className="primary">Armazenar versão 1</button></form><hr /><h2>Nova versão</h2>
    <form className="stack" onSubmit={version}><label>Documento ID<input name="documento" type="number" required /></label>
      <label>Arquivo<input name="arquivo" type="file" accept=".pdf,.docx,.xlsx,.csv" required />
        <small>PDF, DOCX, XLSX ou CSV · máximo de 20 MB</small></label>
      <button className="primary">Adicionar versão</button></form></section>
    <section className="panel"><div className="panel-title"><h2>Documentos ativos</h2><button onClick={search}>Atualizar</button></div>
      {items.map(d => <article className="doc document-card" key={d.id}>
        <div className="document-header"><div><strong>#{d.id} · {d.titulo}</strong>
          <small>{d.categoria} · {d.versoes.length} versão(ões) · criado por {d.criadoPor.nome}</small></div>
          <button onClick={() => deactivate(d.id)}>Desativar</button></div>
        <div className="document-versions">
          {d.versoes.map(v => <div className="document-version" key={v.versao}>
            <div><strong>Versão {v.versao} · {v.nomeArquivo}</strong>
              <small>Enviada por {v.criadoPor.nome} · {v.tamanho.toLocaleString("pt-BR")} bytes</small>
              <small className="checksum">SHA-256 · {v.checksumSha256}</small></div>
            <button onClick={() => download(
              `/api/v1/documentos/${d.id}/versoes/${v.versao}/arquivo`, token
            )}>Baixar versão {v.versao}</button>
          </div>)}
        </div>
      </article>)}</section></div>;
}

const camposTermo = Object.keys(rotulosCampo) as CampoInstrumento[];
const camposApostilamento: CampoInstrumento[] = ["COORDENADOR", "VIGENCIA_TED_FINAL"];

type DadosAtuaisInstrumento = {
  objeto?: string | null; descricao?: string | null; natureza?: string | null; coordenador: string;
  participes?: string[] | null; valorAtual: number; vigenciaContratualFinal: string; vigenciaTedFinal?: string | null;
};

function valoresAtuaisDoInstrumento(dados: DadosAtuaisInstrumento): Record<CampoInstrumento, string | null> {
  const valores: Record<CampoInstrumento, string | null> = {
    OBJETO: dados.objeto ?? null, DESCRICAO: dados.descricao ?? null, NATUREZA: dados.natureza ?? null,
    COORDENADOR: dados.coordenador, PARTICIPES: dados.participes?.join("\n") ?? null,
    VALOR_ATUAL: Number(dados.valorAtual).toFixed(2),
    VIGENCIA_CONTRATUAL_FINAL: dados.vigenciaContratualFinal,
    VIGENCIA_TED_FINAL: dados.vigenciaTedFinal ?? null
  };
  return valores;
}

function valorAtualDoInstrumento(instrumento: Instrumento | undefined, campo: CampoInstrumento): string | null {
  return instrumento ? valoresAtuaisDoInstrumento(instrumento)[campo] : null;
}

function valorDoEstadoAtual(estado: EstadoAtualInstrumento, campo: CampoInstrumento): string | null {
  return valoresAtuaisDoInstrumento(estado)[campo];
}

function formatarEfeito(campo: CampoInstrumento, valor: string | null): string {
  if (valor === null || valor === "") return "Não informado";
  if (campo === "VALOR_ATUAL") return money(Number(valor));
  if (campo === "VIGENCIA_CONTRATUAL_FINAL" || campo === "VIGENCIA_TED_FINAL") {
    return formatarDataNegocio(valor);
  }
  return valor;
}

function Alterations({ token, notify }: Props) {
  const [tipoAtivo, setTipoAtivo] = useState<"TERMO_ADITIVO" | "APOSTILAMENTO">("TERMO_ADITIVO");
  const [instrumentos, setInstrumentos] = useState<Instrumento[]>([]);
  const [setores, setSetores] = useState<Setor[]>([]);
  const [instrumentoId, setInstrumentoId] = useState(0);
  const [alteracoes, setAlteracoes] = useState<AlteracaoContratual[]>([]);
  const [catalogoAlteracoes, setCatalogoAlteracoes] = useState<AlteracaoContratual[]>([]);
  const [alteracaoId, setAlteracaoId] = useState<number | null>(null);
  const [numero, setNumero] = useState("");
  const [mudancas, setMudancas] = useState<{ campo: CampoInstrumento; valorNovo: string }[]>([
    { campo: "OBJETO", valorNovo: "" }
  ]);
  const [numeroEdicao, setNumeroEdicao] = useState("");
  const [mudancasEdicao, setMudancasEdicao] = useState<{ campo: CampoInstrumento; valorNovo: string }[]>([]);
  const [outraOperacao, setOutraOperacao] = useState<"ORIGINAL" | "RETIFICACAO" | "CANCELAMENTO">("ORIGINAL");
  const [outroTipo, setOutroTipo] = useState<"TERMO_ADITIVO" | "APOSTILAMENTO">("APOSTILAMENTO");
  const [outroNumero, setOutroNumero] = useState("");
  const [outraReferencia, setOutraReferencia] = useState("");
  const [outroCampo, setOutroCampo] = useState<CampoInstrumento>("COORDENADOR");
  const [outroValor, setOutroValor] = useState("");
  const [documentosAlteracao, setDocumentosAlteracao] = useState<Documento[]>([]);
  const [resultadoEfetivacao, setResultadoEfetivacao] = useState<EstadoAtualInstrumento | null>(null);
  const [dataEfetivacao, setDataEfetivacao] = useState("");
  const [ordemOficial, setOrdemOficial] = useState("");
  const instrumento = instrumentos.find(item => item.id === instrumentoId);
  const alteracao = alteracoes.find(item => item.id === alteracaoId);
  const apostilamentoAtivo = tipoAtivo === "APOSTILAMENTO";
  const nomeSingular = apostilamentoAtivo ? "Apostilamento" : "Termo Aditivo";
  const nomePlural = apostilamentoAtivo ? "Apostilamentos" : "Termos Aditivos";
  const nomeSingularMinusculo = apostilamentoAtivo ? "apostilamento" : "termo";
  const camposDisponiveis = apostilamentoAtivo ? camposApostilamento : camposTermo;
  const camposOutraAlteracao = outroTipo === "APOSTILAMENTO" ? camposApostilamento : camposTermo;
  const referenciasCompativeis = catalogoAlteracoes.filter(item =>
    item.tipo === outroTipo && item.estado === "EFETIVADA" && item.operacao !== "CANCELAMENTO");
  const campoInicial: CampoInstrumento = apostilamentoAtivo ? "COORDENADOR" : "OBJETO";

  const loadAlteracoes = useCallback(async (id: number) => {
    const items = await request<AlteracaoContratual[]>(`/api/v1/alteracoes?instrumentoId=${id}`, {}, token);
    setCatalogoAlteracoes(items);
    setAlteracoes(items.filter(item => item.tipo === tipoAtivo));
  }, [tipoAtivo, token]);

  useEffect(() => {
    void Promise.all([
      request<Page<ProcessoAdministrativo>>("/api/v1/processos?page=0&size=100", {}, token),
      request<Setor[]>("/api/v1/setores", {}, token)
    ]).then(([page, catalogo]) => {
      const disponiveis = page.content.flatMap(processo => processo.instrumento ? [processo.instrumento] : []);
      setInstrumentos(disponiveis); setSetores(catalogo.filter(setor => setor.ativo));
    }).catch(() => undefined);
  }, [token]);

  useEffect(() => {
    if (!instrumentoId) { setAlteracoes([]); setCatalogoAlteracoes([]); setAlteracaoId(null); return; }
    setAlteracaoId(null);
    void loadAlteracoes(instrumentoId).catch(error => notify((error as Error).message));
  }, [instrumentoId, loadAlteracoes, notify]);

  useEffect(() => {
    if (!alteracao) return;
    setNumeroEdicao(alteracao.numeroOficial);
    setMudancasEdicao(alteracao.mudancas.map(item => ({ campo: item.campo, valorNovo: item.valorNovo ?? "" })));
  }, [alteracaoId, alteracao]);

  useEffect(() => {
    setResultadoEfetivacao(null);
    setDataEfetivacao("");
    setOrdemOficial("");
    if (!alteracaoId) { setDocumentosAlteracao([]); return; }
    void request<Documento[]>(
      `/api/v1/documentos?proprietarioTipo=${tipoAtivo}&proprietarioId=${alteracaoId}`, {}, token
    ).then(items => setDocumentosAlteracao(items.filter(item => item.ativo && item.categoria === "ASSINADO"
      && item.versoes[0]?.tipoMime === "application/pdf")))
      .catch(() => setDocumentosAlteracao([]));
  }, [alteracaoId, tipoAtivo, token]);

  function alteracaoPrevaleceNoCampo(campo: CampoInstrumento) {
    if (!dataEfetivacao || !ordemOficial) return true;
    const precedenciaAtual = alteracao?.estadoAtualInstrumento.precedenciaPorCampo?.[campo];
    return !precedenciaAtual || precedenciaAtual.dataEfetivacao < dataEfetivacao
      || (precedenciaAtual.dataEfetivacao === dataEfetivacao
        && precedenciaAtual.ordemOficial < Number(ordemOficial));
  }

  function mudarCampo(index: number, campo: CampoInstrumento) {
    setMudancas(items => items.map((item, atual) => atual === index ? { campo, valorNovo: "" } : item));
  }

  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    try {
      const result = await request<AlteracaoContratual>("/api/v1/alteracoes", { method: "POST", body: JSON.stringify({
        instrumentoId, tipo: tipoAtivo, numeroOficial: numero, operacao: "ORIGINAL", referenciaId: null,
        mudancas: mudancas.map(item => ({
          campo: item.campo, valorAnterior: valorAtualDoInstrumento(instrumento, item.campo), valorNovo: item.valorNovo
        }))
      }) }, token);
      setAlteracoes(items => [result, ...items.filter(item => item.id !== result.id)]);
      setCatalogoAlteracoes(items => [result, ...items.filter(item => item.id !== result.id)]);
      setAlteracaoId(result.id); setNumero(""); setMudancas([{ campo: campoInicial, valorNovo: "" }]);
      notify(`Rascunho #${result.id} criado sem alterar o instrumento vigente.`);
    } catch (error) { notify((error as Error).message); }
  }

  async function updateDraft(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!alteracao) return;
    try {
      const result = await request<AlteracaoContratual>(`/api/v1/alteracoes/${alteracao.id}`, {
        method: "PUT", body: JSON.stringify({ numeroOficial: numeroEdicao, mudancas: mudancasEdicao.map(item => ({
          campo: item.campo, valorAnterior: valorAtualDoInstrumento(instrumento, item.campo), valorNovo: item.valorNovo
        })) })
      }, token);
      setAlteracoes(items => items.map(item => item.id === result.id ? result : item));
      setCatalogoAlteracoes(items => items.map(item => item.id === result.id ? result : item));
      notify(`Rascunho do ${nomeSingular} atualizado.`);
    } catch (error) { notify((error as Error).message); }
  }

  async function createOther(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    try {
      const result = await request<AlteracaoContratual>("/api/v1/alteracoes", { method: "POST", body: JSON.stringify({
        instrumentoId, tipo: outroTipo, numeroOficial: outroNumero, operacao: outraOperacao,
        referenciaId: outraOperacao === "ORIGINAL" ? null : Number(outraReferencia),
        mudancas: outraOperacao === "CANCELAMENTO" ? [] : [{
          campo: outroCampo, valorAnterior: valorAtualDoInstrumento(instrumento, outroCampo), valorNovo: outroValor
        }]
      }) }, token);
      if (outroTipo === tipoAtivo) {
        setAlteracoes(items => [result, ...items.filter(item => item.id !== result.id)]); setAlteracaoId(result.id);
      }
      setCatalogoAlteracoes(items => [result, ...items.filter(item => item.id !== result.id)]);
      setOutroNumero(""); setOutraReferencia(""); setOutroValor("");
      notify(`${outroTipo === "APOSTILAMENTO" ? "Apostilamento" : "Termo Aditivo"} #${result.id} criado.`);
    } catch (error) { notify((error as Error).message); }
  }

  async function move(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!alteracao) return;
    const form = e.currentTarget; const data = new FormData(form);
    try {
      await request("/api/v1/movimentacoes", { method: "POST", body: JSON.stringify({
        contextoTipo: tipoAtivo, contextoId: alteracao.id, setorDestinoId: Number(data.get("setorDestino")),
        dataMovimentacao: data.get("dataMovimentacao"), observacao: data.get("observacao")
      }) }, token);
      const atualizado = await request<AlteracaoContratual>(`/api/v1/alteracoes/${alteracao.id}`, {}, token);
      setAlteracoes(items => items.map(item => item.id === atualizado.id ? atualizado : item));
      notify(`Movimentação do ${nomeSingular} registrada.`); form.reset();
    } catch (error) { notify((error as Error).message); }
  }

  async function efetivarAlteracao(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!alteracao) return;
    const f = new FormData(e.currentTarget);
    try {
      const result = await request<AlteracaoContratual>(`/api/v1/alteracoes/${alteracao.id}/efetivacao`, {
        method: "POST", body: JSON.stringify({
          dataEfetivacao: f.get("dataEfetivacao"), ordemOficial: Number(f.get("ordemOficial")),
          documentoAssinadoId: Number(f.get("documentoAssinadoId"))
        })
      }, token);
      setAlteracoes(items => items.map(item => item.id === result.id ? result : item));
      setCatalogoAlteracoes(items => items.map(item => item.id === result.id ? result : item));
      setResultadoEfetivacao(result.estadoAtualInstrumento);
      setInstrumentos(items => items.map(item => item.id === result.instrumentoId ? {
        ...item, objeto: result.estadoAtualInstrumento.objeto,
        descricao: result.estadoAtualInstrumento.descricao ?? undefined,
        natureza: result.estadoAtualInstrumento.natureza,
        coordenador: result.estadoAtualInstrumento.coordenador,
        participes: result.estadoAtualInstrumento.participes,
        valorAtual: result.estadoAtualInstrumento.valorAtual,
        vigenciaContratualFinal: result.estadoAtualInstrumento.vigenciaContratualFinal,
        vigenciaTedFinal: result.estadoAtualInstrumento.vigenciaTedFinal ?? undefined
      } : item));
      notify(`${nomeSingular} efetivado e estado atual recomputado.`);
    }
    catch (error) { notify((error as Error).message); }
  }

  function selecionarTipo(tipo: "TERMO_ADITIVO" | "APOSTILAMENTO") {
    setTipoAtivo(tipo);
    setAlteracaoId(null);
    setNumero("");
    setMudancas([{ campo: tipo === "APOSTILAMENTO" ? "COORDENADOR" : "OBJETO", valorNovo: "" }]);
    setResultadoEfetivacao(null);
  }

  return <div className="stack alteracoes-contratuais"><section className="panel"><div className="form-actions" aria-label="Tipo de alteração">
    <button type="button" className={tipoAtivo === "TERMO_ADITIVO" ? "primary" : ""}
      onClick={() => selecionarTipo("TERMO_ADITIVO")}>Termos Aditivos</button>
    <button type="button" className={tipoAtivo === "APOSTILAMENTO" ? "primary" : ""}
      onClick={() => selecionarTipo("APOSTILAMENTO")}>Apostilamentos</button>
  </div><h2>Preparar {nomeSingular}</h2>
    <p className="muted">{apostilamentoAtivo
      ? "O rascunho altera somente dados não contratuais aprovados e mantém intacto o estado vigente do instrumento."
      : "O rascunho registra condições propostas e mantém intacto o estado vigente do instrumento."}</p>
    <form className="stack" onSubmit={create}>
      <div className="inline-form"><label>Instrumento Contratual<select required value={instrumentoId || ""}
        onChange={e => setInstrumentoId(Number(e.target.value))}><option value="">Selecione</option>
        {instrumentos.map(item => <option key={item.id} value={item.id}>{item.numero} · {item.tipo.replaceAll("_", " ")}</option>)}</select></label>
        <label>Identificação do {nomeSingularMinusculo}<input required value={numero} onChange={e => setNumero(e.target.value)} /></label></div>
      <h3>Mudanças propostas</h3>
      {mudancas.map((mudanca, index) => <div className="inline-form mudanca-alteracao" key={index}>
        <label>{index === 0 ? "Campo da mudança" : `Campo da mudança ${index + 1}`}<select value={mudanca.campo}
          onChange={e => mudarCampo(index, e.target.value as CampoInstrumento)}>{camposDisponiveis.map(campo =>
            <option key={campo} value={campo}>{rotulosCampo[campo]}</option>)}</select></label>
        <label>{index === 0 ? "Valor anterior" : `Valor anterior ${index + 1}`}<input readOnly
          value={valorAtualDoInstrumento(instrumento, mudanca.campo) ?? "Não informado"} /></label>
        <label>{index === 0 ? "Novo valor" : `Novo valor ${index + 1}`}<input required value={mudanca.valorNovo}
          onChange={e => setMudancas(items => items.map((item, atual) => atual === index ? { ...item, valorNovo: e.target.value } : item))} /></label>
        {mudancas.length > 1 && <button type="button" onClick={() => setMudancas(items => items.filter((_, atual) => atual !== index))}>Remover</button>}
      </div>)}
      <div className="form-actions"><button type="button" onClick={() => setMudancas(items => [...items, { campo: campoInicial, valorNovo: "" }])}>Adicionar mudança</button>
        <button className="primary" disabled={!instrumentoId}>Criar rascunho</button></div>
    </form></section>

    {instrumentoId > 0 && <div className="grid two"><section className="panel"><div className="panel-title"><h2>{nomePlural} do instrumento</h2>
      <button onClick={() => void loadAlteracoes(instrumentoId)}>Atualizar</button></div>
      {alteracoes.length === 0 && <p className="muted">Nenhum {nomeSingular} preparado.</p>}
      <div className="alteracao-lista">{alteracoes.map(item => <button className={item.id === alteracaoId ? "selected" : ""} key={item.id}
        onClick={() => setAlteracaoId(item.id)}><strong>{item.numeroOficial}</strong><small>#{item.id} · {item.estado}</small></button>)}</div>
    </section>

    <section className="panel">{alteracao ? <><div className="panel-title"><div><h2>{alteracao.numeroOficial}</h2>
      <small>{nomeSingular} #{alteracao.id} · {alteracao.estado}</small></div><Badge value={alteracao.estado} /></div>
      <section className="stack estado-resultante" aria-label="Estado atual do Instrumento Contratual">
        <div className="panel-title"><div><h3>Estado atual do Instrumento Contratual</h3>
          <p className="muted">Dados vigentes reconstruídos pela data de efetivação e pela ordem oficial.</p></div>
          <Badge value={alteracao.estadoAtualInstrumento.statusProcesso} /></div>
        {camposTermo.map(campo => <div className="mudanca-resumo" key={campo}>
          <strong>{rotulosCampo[campo]}</strong>
          <span>{formatarEfeito(campo, valorDoEstadoAtual(alteracao.estadoAtualInstrumento, campo))}</span>
        </div>)}
      </section>
      {alteracao.cadeia && alteracao.cadeia.length > 0 && <section className="stack" aria-label="Cadeia da alteração">
        <div><h3>Cadeia da alteração</h3><p className="muted">Origem, retificações e cancelamentos permanecem visíveis sem reescrever o histórico.</p></div>
        <ol className="timeline">{alteracao.cadeia.map(item => <li key={item.id}><span className="timeline-marker" />
          <div><strong>{item.numeroOficial}</strong>
            <time>{item.operacao.replaceAll("_", " ")} · #{item.id}
              {item.referenciaId ? ` · referência #${item.referenciaId}` : " · origem"}
              {item.dataEfetivacao ? ` · ${formatarDataNegocio(item.dataEfetivacao)} · ordem ${item.ordemOficial}` : " · rascunho"}</time>
            <small>{item.produzEfeitoAtual ? "Produz efeito" : "Sem efeito atual"}</small>
            {Object.entries(item.valoresProduzidos).map(([campo, valor]) => <p key={campo}>
              {item.operacao === "CANCELAMENTO" ? "Valor restaurado" : "Valor produzido"}: {formatarEfeito(campo as CampoInstrumento, valor)}
            </p>)}</div></li>)}</ol>
      </section>}
      <h3>Mudanças do rascunho</h3>{alteracao.mudancas.map(mudanca => <div className="mudanca-resumo" key={mudanca.campo}>
        <strong>{rotulosCampo[mudanca.campo]}</strong><span>{mudanca.valorAnterior ?? "Não informado"} → {mudanca.valorNovo ?? "Não informado"}</span>
      </div>)}
      {alteracao.estado === "RASCUNHO" && <form className="stack edicao-alteracao" onSubmit={updateDraft}>
        <label>Identificação do rascunho<input required value={numeroEdicao} onChange={e => setNumeroEdicao(e.target.value)} /></label>
        {mudancasEdicao.map((mudanca, index) => <div className="inline-form" key={index}>
          <label>{index === 0 ? "Campo editado" : `Campo ${index + 1} do rascunho`}<select value={mudanca.campo}
            onChange={e => setMudancasEdicao(items => items.map((item, atual) => atual === index
              ? { campo: e.target.value as CampoInstrumento, valorNovo: "" } : item))}>
            {camposDisponiveis.map(campo => <option key={campo} value={campo}>{rotulosCampo[campo]}</option>)}</select></label>
          <label>{index === 0 ? "Valor anterior editado" : `Estado atual do campo ${index + 1}`}<input readOnly
            value={valorAtualDoInstrumento(instrumento, mudanca.campo) ?? "Não informado"} /></label>
          <label>{index === 0 ? "Novo valor editado" : `Conteúdo proposto ${index + 1}`}<input required value={mudanca.valorNovo}
            onChange={e => setMudancasEdicao(items => items.map((item, atual) => atual === index
              ? { ...item, valorNovo: e.target.value } : item))} /></label>
          {mudancasEdicao.length > 1 && <button type="button"
            onClick={() => setMudancasEdicao(items => items.filter((_, atual) => atual !== index))}>Remover</button>}
        </div>)}
        <div className="form-actions"><button type="button"
          onClick={() => setMudancasEdicao(items => [...items, { campo: campoInicial, valorNovo: "" }])}>Adicionar mudança ao rascunho</button>
          <button className="primary">Salvar rascunho</button></div>
      </form>}
      {alteracao.estado === "RASCUNHO" && <section className="stack efetivacao-alteracao" aria-label="Efeitos a confirmar">
        <div><h3>Efeitos a confirmar</h3><p className="muted">Confira o estado vigente e cada valor proposto antes de tornar o {nomeSingularMinusculo} oficial.</p></div>
        {alteracao.mudancas.map(mudanca => <div className="efeito-alteracao" key={mudanca.campo}>
          <strong>{rotulosCampo[mudanca.campo]}</strong>
          <span><small>Estado vigente</small>{formatarEfeito(mudanca.campo,
            valorAtualDoInstrumento(instrumento, mudanca.campo))}</span>
          <span><small>{dataEfetivacao && ordemOficial ? "Resultado previsto" : "Efeito proposto"}</small>
            {formatarEfeito(mudanca.campo, alteracaoPrevaleceNoCampo(mudanca.campo)
              ? mudanca.valorNovo ?? null : valorAtualDoInstrumento(instrumento, mudanca.campo))}
            {dataEfetivacao && ordemOficial && !alteracaoPrevaleceNoCampo(mudanca.campo)
              && <em>Não prevalece sobre a alteração oficial mais recente.</em>}</span>
        </div>)}
        <form className="inline-form" onSubmit={efetivarAlteracao}>
          <label>Data de efetivação<input name="dataEfetivacao" type="date" required value={dataEfetivacao}
            onChange={e => setDataEfetivacao(e.target.value)} /></label>
          <label>Ordem oficial<input name="ordemOficial" type="number" min="1" required value={ordemOficial}
            onChange={e => setOrdemOficial(e.target.value)} /></label>
          <label>PDF assinado<select name="documentoAssinadoId" required defaultValue=""><option value="">Selecione</option>
            {documentosAlteracao.map(documento => <option key={documento.id} value={documento.id}>
              #{documento.id} · {documento.titulo}</option>)}</select></label>
          <button className="primary">Confirmar efetivação</button>
        </form>
        {documentosAlteracao.length === 0 && <p className="muted">Anexe um Documento Assinado em PDF a este {nomeSingular} antes da confirmação.</p>}
      </section>}
      {resultadoEfetivacao && <section className="stack estado-resultante" aria-label="Estado resultante">
        <div className="panel-title"><div><h3>Estado resultante</h3><p className="muted">Dados correntes após aplicar a cronologia oficial.</p></div>
          <Badge value={resultadoEfetivacao.statusProcesso} /></div>
        {alteracao.mudancas.map(mudanca => <div className="mudanca-resumo" key={mudanca.campo}>
          <strong>{rotulosCampo[mudanca.campo]}</strong>
          <span>{formatarEfeito(mudanca.campo, valorDoEstadoAtual(resultadoEfetivacao, mudanca.campo))}</span>
        </div>)}
      </section>}
      <hr /><h3>Tramitação própria</h3><div className="tramitacao-metrics"><div><small>Setor atual</small>
        <strong>{alteracao.tramitacao?.setorAtual ? `${alteracao.tramitacao.setorAtual.sigla} · setor atual` : "Ainda não tramitado"}</strong></div><div><small>Movimentações</small>
        <strong>{alteracao.tramitacao?.movimentacoes.length ?? 0}</strong></div></div>
      <form className="inline-form" onSubmit={move}><label>Data da movimentação<input name="dataMovimentacao" type="date" required /></label>
        <label>Setor de destino<select name="setorDestino" required defaultValue=""><option value="">Selecione</option>
          {setores.map(setor => <option key={setor.id} value={setor.id}>{setor.sigla} · {setor.nome}</option>)}</select></label>
        <label>Observação da movimentação<input name="observacao" /></label><button className="primary">Registrar movimentação</button></form>
      <ol className="timeline">{alteracao.tramitacao?.movimentacoes.map(item => <li key={item.id}><span className="timeline-marker" />
        <div><strong>{item.setorDestino.sigla}</strong><time>{formatarDataNegocio(item.dataMovimentacao)} · sequência {item.sequenciaDiaria}</time>
          {item.observacao && <p>{item.observacao}</p>}<small>Registrado por {item.autor.nome}</small></div></li>)}</ol>
    </> : <p className="muted">Selecione um {nomeSingularMinusculo} para editar o rascunho e acompanhar sua tramitação.</p>}</section></div>}

    <section className="panel"><h2>Outras operações contratuais</h2>
      <p className="muted">Use este fluxo para Apostilamento, retificação ou cancelamento de uma alteração efetivada.</p>
      <form className="inline-form" onSubmit={createOther}>
        <label>Tipo da alteração<select value={outroTipo} onChange={e => {
          const tipo = e.target.value as typeof outroTipo;
          setOutroTipo(tipo); setOutroCampo(tipo === "APOSTILAMENTO" ? "COORDENADOR" : "OBJETO");
          setOutraReferencia(""); setOutroValor("");
        }}>
          <option value="APOSTILAMENTO">Apostilamento</option><option value="TERMO_ADITIVO">Termo Aditivo</option></select></label>
        <label>Operação da alteração<select value={outraOperacao} onChange={e => setOutraOperacao(e.target.value as typeof outraOperacao)}>
          <option value="ORIGINAL">Original</option><option value="RETIFICACAO">Retificação</option>
          <option value="CANCELAMENTO">Cancelamento</option></select></label>
        <label>Identificação da outra alteração<input required value={outroNumero} onChange={e => setOutroNumero(e.target.value)} /></label>
        {outraOperacao !== "ORIGINAL" && <label>Alteração de referência<select required value={outraReferencia}
          onChange={e => setOutraReferencia(e.target.value)}><option value="">Selecione</option>
          {referenciasCompativeis.map(item => <option key={item.id} value={item.id}>
            {item.numeroOficial} · #{item.id}</option>)}</select></label>}
        {outraOperacao !== "CANCELAMENTO" && <><label>Campo da outra alteração<select value={outroCampo}
          onChange={e => { setOutroCampo(e.target.value as CampoInstrumento); setOutroValor(""); }}>
          {camposOutraAlteracao.map(campo => <option key={campo} value={campo}>{rotulosCampo[campo]}</option>)}</select></label>
          <label>Estado atual do campo<input readOnly value={valorAtualDoInstrumento(instrumento, outroCampo) ?? "Não informado"} /></label>
          <label>Valor proposto na outra alteração<input required value={outroValor} onChange={e => setOutroValor(e.target.value)} /></label></>}
        <button className="primary" disabled={!instrumentoId}>Criar outra alteração</button>
      </form>
    </section>

    </div>;
}

function Reports({ token, notify }: Props) {
  type RelatorioGerado = {
    id: number; tipo: string; formato: string; filtros: Record<string, string>;
    criadoPor: { id: number; login: string; nome: string }; criadoEm: string;
    checksumSha256: string | null; chaveArmazenamento: string; tamanhoBytes: number;
    nomeArquivo: string;
  };
  const [items, setItems] = useState<RelatorioGerado[]>([]);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const load = useCallback(() => request<typeof items>("/api/v1/relatorios", {}, token).then(setItems), [token]);
  useEffect(() => { void load(); }, [load]);
  async function generate(type: string, format: string) {
    const commonFilters = ["numero", "origem", "tipo", "status", "vigenciaContratual", "vigenciaTed"];
    const reportFilters: Record<string, string[]> = {
      ANUAL_PROCESSOS: [...commonFilters, "ano"],
      INSTRUMENTOS_POR_TIPO: commonFilters,
      HISTORICO_TRAMITACOES: [...commonFilters, "contexto", "dataInicial", "dataFinal"],
      VIGENCIAS: commonFilters,
      CONSOLIDADO: commonFilters,
    };
    try { await request("/api/v1/relatorios", { method: "POST", body: JSON.stringify({
      tipo: type, formato: format,
      filtros: Object.fromEntries(Object.entries(filters)
        .filter(([name, value]) => value && reportFilters[type]?.includes(name)))
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
      <label>Contexto<select value={filters.contexto ?? ""}
        onChange={e => setFilters({ ...filters, contexto: e.target.value })}>
        <option value="">Todos</option>
        <option value="FORMALIZACAO">Formalização</option>
        <option value="TERMO_ADITIVO">Termo Aditivo</option>
        <option value="APOSTILAMENTO">Apostilamento</option>
      </select></label>
      <label>Status<select value={filters.status ?? ""} onChange={e => setFilters({ ...filters, status: e.target.value })}>
        <option value="">Todos</option>{["EM_FORMALIZACAO", "EM_VIGENCIA", "CONCLUIDO"].map(x => <option key={x}>{x}</option>)}</select></label>
      <label>Vigência contratual<select value={filters.vigenciaContratual ?? ""}
        onChange={e => setFilters({ ...filters, vigenciaContratual: e.target.value })}>
        <option value="">Todas</option>{["VALIDA", "PROXIMA_VENCIMENTO", "VENCIDA", "NAO_INFORMADA"].map(x =>
          <option key={x}>{x}</option>)}</select></label>
      <label>Vigência TED<select value={filters.vigenciaTed ?? ""}
        onChange={e => setFilters({ ...filters, vigenciaTed: e.target.value })}>
        <option value="">Todas</option>{["VALIDA", "PROXIMA_VENCIMENTO", "VENCIDA", "NAO_INFORMADA"].map(x =>
          <option key={x}>{x}</option>)}</select></label></div>
    <h2>Gerar relatório</h2><div className="report-actions">
    {["ANUAL_PROCESSOS", "INSTRUMENTOS_POR_TIPO", "HISTORICO_TRAMITACOES", "VIGENCIAS", "CONSOLIDADO"].map(type =>
      <div key={type}><strong>{type.replaceAll("_", " ")}</strong>{["PDF", "XLSX", "CSV"].map(format =>
        <button key={format} onClick={() => generate(type, format)}>{format}</button>)}</div>)}</div>
    <h2>Histórico</h2>{items.map(item => <article className="doc" key={item.id}><div className="report-metadata">
      <strong>{item.tipo.replaceAll("_", " ")}</strong>
      <small>{item.nomeArquivo} · {item.formato} · {new Date(item.criadoEm).toLocaleString("pt-BR")}</small>
      <small>Gerado por {item.criadoPor.nome} ({item.criadoPor.login})</small>
      <small>Filtros: {Object.entries(item.filtros).length
        ? Object.entries(item.filtros).map(([nome, valor]) => `${nome}: ${valor}`).join(" · ")
        : "Sem filtros"}</small>
      <small className="checksum">SHA-256 · {item.checksumSha256 ?? "Não disponível"}</small>
      <small>Armazenamento · {item.chaveArmazenamento} · {item.tamanhoBytes} bytes</small></div>
      <button onClick={() => download(`/api/v1/relatorios/${item.id}/arquivo`, token)}>Baixar</button></article>)}</section>;
}

function Notifications({ token, onVerProcessoAdministrativo }: {
  token: string;
  onVerProcessoAdministrativo: (notificacaoId: number) => void;
}) {
  const [items, setItems] = useState<NotificacaoInterna[]>([]);
  const load = useCallback(() => request<typeof items>("/api/v1/notificacoes", {}, token).then(setItems), [token]);
  useEffect(() => { void load(); }, [load]);
  async function read(id: number) { await request(`/api/v1/notificacoes/${id}/lida`, { method: "PATCH" }, token); await load(); }
  const contratuais = items.filter(item => item.tipo === "ALERTA_VIGENCIA_CONTRATUAL");
  const ted = items.filter(item => item.tipo === "ALERTA_VIGENCIA_TED");
  const outras = items.filter(item => !item.tipo.startsWith("ALERTA_VIGENCIA_"));
  return <section className="panel notifications-panel"><h2>Caixa de entrada</h2>
    <NotificationGroup titulo="Alertas de Vigência Contratual" items={contratuais}
      onRead={read} onVerProcessoAdministrativo={onVerProcessoAdministrativo} />
    <NotificationGroup titulo="Alertas de Vigência do TED" items={ted}
      onRead={read} onVerProcessoAdministrativo={onVerProcessoAdministrativo} />
    <NotificationGroup titulo="Outras Notificações Internas" items={outras}
      onRead={read} onVerProcessoAdministrativo={onVerProcessoAdministrativo} />
    {!items.length && <p className="empty">Nenhuma notificação.</p>}</section>;
}

function NotificationGroup({ titulo, items, onRead, onVerProcessoAdministrativo }: {
  titulo: string;
  items: NotificacaoInterna[];
  onRead: (id: number) => Promise<void>;
  onVerProcessoAdministrativo: (notificacaoId: number) => void;
}) {
  if (!items.length) return null;
  return <section className="notification-group" aria-label={titulo}><h3>{titulo}</h3>{items.map(notificacao => <article
    className={`notification ${notificacao.lida ? "read" : ""}`} key={notificacao.id}>
    <span aria-hidden="true">{notificacao.lida ? "✓" : "•"}</span><div>
      <strong>{notificacao.tipo.replaceAll("_", " ")}</strong>
      <p>{notificacao.mensagem}</p><small>{new Date(notificacao.criadaEm).toLocaleString("pt-BR")}</small></div>
    <div className="notification-actions">
      {notificacao.lida
        ? <span>Lida</span>
        : <button onClick={() => void onRead(notificacao.id)}>Marcar como lida</button>}
      {notificacao.processoId !== null &&
        <button onClick={() => onVerProcessoAdministrativo(notificacao.id)}>
          Ver Processo Administrativo
        </button>}
    </div>
  </article>)}
  </section>;
}

function AuditRecords({ token }: { token: string }) {
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
        <option value="SUCESSO">Sucesso</option><option value="FALHA">Falha</option></select></label>
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
          <td><strong>{event.acao}</strong></td><td><Badge value={event.resultado} /></td>
          <td>{event.ator ? <div className="audit-actor"><strong>{event.ator.nome}</strong>
            <small>@{event.ator.login}</small></div> : <span className="muted">Ator não identificado</span>}</td>
          <td><div className="audit-object"><strong>{event.objeto.tipo.replaceAll("_", " ")}</strong>
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

function Administration({ token, notify }: Props) {
  type UsuarioAdmin = {
    id: number; nome: string; email: string; login: string; perfil: string;
    ativo: boolean; senhaTemporaria: boolean;
  };
  const [users, setUsers] = useState<UsuarioAdmin[]>([]);
  const [selectedUser, setSelectedUser] = useState<UsuarioAdmin | null>(null);
  const [setores, setSetores] = useState<Setor[]>([]);
  const [setorEmEdicao, setSetorEmEdicao] = useState<Setor | null>(null);
  const load = useCallback(() => Promise.all([
    request<typeof users>("/api/v1/admin/usuarios", {}, token),
    request<Setor[]>("/api/v1/admin/setores", {}, token)
  ]).then(([usuariosCarregados, setoresCarregados]) => {
    setUsers(usuariosCarregados); setSetores(setoresCarregados);
  }), [token]);
  useEffect(() => { void load(); }, [load]);
  async function user(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    try { await request("/api/v1/admin/usuarios", { method: "POST", body: JSON.stringify({
      nome: f.get("nome"), email: f.get("email"), login: f.get("login"),
      senhaTemporaria: f.get("senha"), perfil: f.get("perfil")
    }) }, token); notify("Usuário criado com senha temporária."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  function extrairIdentidadeSetor(formulario: HTMLFormElement) {
    const dadosFormulario = new FormData(formulario);
    return { sigla: dadosFormulario.get("sigla"), nome: dadosFormulario.get("nome") };
  }
  async function criarSetor(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const formulario = e.currentTarget;
    try { await request("/api/v1/admin/setores", {
      method: "POST", body: JSON.stringify(extrairIdentidadeSetor(formulario))
    }, token); formulario.reset(); notify("Setor incluído no catálogo."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function editarSetor(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (!setorEmEdicao) return;
    try { await request(`/api/v1/admin/setores/${setorEmEdicao.id}`, {
      method: "PUT", body: JSON.stringify(extrairIdentidadeSetor(e.currentTarget))
    }, token); setSetorEmEdicao(null); notify("Identidade do setor atualizada."); await load(); }
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
    <section className="panel catalogo-setores"><h2>Catálogo de setores</h2>
      <p className="muted">Somente setores ativos ficam disponíveis como novo destino de tramitação.</p>
      <form className="inline-form" onSubmit={criarSetor}>
        <label>Sigla<input name="sigla" maxLength={30} required /></label>
        <label>Nome<input name="nome" maxLength={150} required /></label>
        <button className="primary">Adicionar</button>
      </form>
      {setorEmEdicao && <form className="edicao-setor stack" onSubmit={editarSetor} key={setorEmEdicao.id}>
        <div className="panel-title"><h3>Editar setor {setorEmEdicao.sigla}</h3>
          <button type="button" onClick={() => setSetorEmEdicao(null)}>Cancelar edição</button></div>
        <div className="inline-form">
          <label>Sigla do setor<input name="sigla" maxLength={30} defaultValue={setorEmEdicao.sigla} required /></label>
          <label>Nome do setor<input name="nome" maxLength={150} defaultValue={setorEmEdicao.nome} required /></label>
          <button className="primary">Salvar alterações</button>
        </div>
      </form>}
      <div className="lista-setores">{setores.map(s =>
        <article className={`doc linha-setor ${s.ativo ? "ativo" : "inativo"}`} key={s.id}>
          <div><strong>{s.sigla}</strong><small>{s.nome}</small></div>
          <Badge value={s.ativo ? "ATIVO" : "INATIVO"} />
          <div className="acoes-setor">
            <button aria-label={`Editar setor ${s.sigla}`} onClick={() => setSetorEmEdicao(s)}>Editar</button>
            <button onClick={() => toggleSector(s.id, s.ativo)}>{s.ativo ? "Desativar" : "Reativar"}</button>
          </div>
        </article>)}
        {!setores.length && <p className="empty">Nenhum setor cadastrado.</p>}
      </div>
    </section></div>;
}

function Badge({ value }: { value: string }) { return <span className={`badge ${value.toLowerCase()}`}>{value.replaceAll("_", " ")}</span>; }
function Metric({ label, value }: { label: string; value: string | number }) {
  return <article className="metric"><small>{label}</small><strong>{value}</strong></article>;
}
function money(value: number) { return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value); }
type Props = { token: string; notify: (message: string) => void };
