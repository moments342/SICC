import { useCallback, useEffect, useRef, useState } from "react";
import {
  persistSession,
  readStoredSession,
  SESSION_REJECTED_EVENT
} from "./session";
import type { Session } from "./session";
import type { ProcessoAdministrativo, ProcessDocumentTarget, OpcaoInstrumentoAlteracao, NotificacaoInterna } from "./models";
import { useResource } from "./useResource";
import { ROUTE_CHANGE, routePage } from "./useRouteState";
import type { ProcessFilterRequest } from "./components/processes/ProcessCatalog";
import { allowedPage, pageFromLocation, pageLabels } from "./navigation";
import { rotuloDominio } from "./domainLabels";
import { PasswordChange, PublicAccess } from "./pages/AccessPages";
import { Administration } from "./pages/AdministrationPage";
import { Alterations } from "./pages/AlterationsPage";
import { AuditRecords } from "./pages/AuditRecordsPage";
import { Dashboard } from "./pages/DashboardPage";
import { Documents } from "./pages/DocumentsPage";
import { Notifications } from "./pages/NotificationsPage";
import { Processes } from "./pages/ProcessesPage";
import { Reports } from "./pages/ReportsPage";
import { PageFeedback } from "./components/Feedback";
import { SessionDrafts, useHasUnsavedDrafts } from "./components/SessionDrafts";
import { ReportGeneration } from "./components/ReportGeneration";
import { Confirmations, useConfirmation } from "./components/Confirmation";

export default function App() {
  const [session, setSession] = useState<Session | null>(readStoredSession);
  const requestedRoute = useRef(window.location.hash);

  const saveSession = useCallback((value: Session | null) => {
    const requestedPage = requestedRoute.current.replace(/^#\/?/, "").split("?")[0];
    const destination = value && requestedPage && allowedPage(requestedPage, value.perfil) === requestedPage
      ? requestedRoute.current : value ? "#/dashboard" : "";
    requestedRoute.current = "";
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}${destination}`);
    setSession(value);
    persistSession(value);
  }, []);

  useEffect(() => {
    const rejectSession = (event: Event) => {
      if ((event as CustomEvent<string>).detail === session?.token) saveSession(null);
    };
    window.addEventListener(SESSION_REJECTED_EVENT, rejectSession);
    return () => window.removeEventListener(SESSION_REJECTED_EVENT, rejectSession);
  }, [saveSession, session?.token]);

  useEffect(() => {
    if (!session) document.title = "Consulta pública · SICC";
    else if (session.trocaSenhaObrigatoria) document.title = "Troca de senha · SICC";
  }, [session]);

  if (!session) return <PublicAccess onLogin={saveSession} />;
  if (session.trocaSenhaObrigatoria) {
    return <PasswordChange session={session} onDone={() => saveSession(null)} />;
  }

  return <SessionDrafts key={session.token}><Confirmations><ReportGeneration>
    <Workspace session={session} onLogout={() => saveSession(null)} />
  </ReportGeneration></Confirmations></SessionDrafts>;
}

function Workspace({ session, onLogout }: { session: Session; onLogout: () => void }) {
  const inbox = useResource<NotificacaoInterna[]>("/api/v1/notificacoes", session.token);
  const unread = inbox.data?.filter(item => !item.lida).length ?? 0;
  const savedRoutes = useRef<Record<string, string>>({});
  const [menuOpen, setMenuOpen] = useState(false);
  const [visited, setVisited] = useState(() => new Set([pageFromLocation(session.perfil)]));
  const [tab, setTab] = useState(() => pageFromLocation(session.perfil));
  const [notificacaoEmFoco, setNotificacaoEmFoco] = useState<number | null>(null);
  const [documentContext, setDocumentContext] = useState<{ processo: ProcessoAdministrativo; target: ProcessDocumentTarget } | null>(null);
  const [alterationContext, setAlterationContext] = useState<OpcaoInstrumentoAlteracao | null>(null);
  const [processFilterRequest, setProcessFilterRequest] = useState<ProcessFilterRequest | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const hasUnsaved = useHasUnsavedDrafts();
  const confirm = useConfirmation();
  const isAdmin = session.perfil === "ADMINISTRADOR_DIPAC";

  function navigate(next: string) {
    const destination = allowedPage(next, session.perfil);
    if (destination === tab) return;
    savedRoutes.current[tab] = window.location.hash;
    window.history.pushState(null, "", savedRoutes.current[destination] ?? `#/${destination}`);
    setTab(destination);
  }
  useEffect(() => {
    const refresh = () => { if (document.visibilityState === "visible") inbox.reload(); };
    window.addEventListener("focus", refresh); document.addEventListener("visibilitychange", refresh);
    const timer = window.setInterval(refresh, 60_000);
    return () => { window.removeEventListener("focus", refresh); document.removeEventListener("visibilitychange", refresh); window.clearInterval(timer); };
  }, [inbox.reload]);
  useEffect(() => {
    const restore = () => {
      const destination = pageFromLocation(session.perfil);
      if (destination !== routePage()) window.history.replaceState(null, "", `#/${destination}`);
      setTab(destination);
    };
    window.addEventListener("popstate", restore);
    window.addEventListener("hashchange", restore);
    window.addEventListener(ROUTE_CHANGE, restore);
    return () => {
      window.removeEventListener("popstate", restore);
      window.removeEventListener("hashchange", restore);
      window.removeEventListener(ROUTE_CHANGE, restore);
    };
  }, [session.perfil]);
  useEffect(() => {
    setVisited(current => new Set(current).add(tab));
    setMenuOpen(false);
    if (tab !== routePage()) window.history.replaceState(null, "", `#/${tab}`);
    document.title = `${pageLabels[tab]} · SICC`;
    heading.current?.focus();
  }, [tab]);

  async function logout() {
    if (hasUnsaved && !await confirm({ title: "Sair sem salvar?", message: "Há preenchimento não salvo. Sair da sessão descartará esse preenchimento.",
      confirmLabel: "Descartar e sair", destructive: true })) return;
    onLogout();
  }

  return (
    <div className="shell"><a className="skip-link" href="#workspace-content" onClick={event => { event.preventDefault(); document.getElementById("workspace-content")?.focus(); }}>Ir para o conteúdo</a>
      <aside>
        <div className="brand"><div><strong>SICC</strong><small>DIPAC · UFGD</small></div></div>
        <button className="mobile-menu" aria-expanded={menuOpen} aria-controls="workspace-menu" onClick={() => setMenuOpen(value => !value)}>Menu · {pageLabels[tab]}</button>
        <nav id="workspace-menu" className={menuOpen ? "menu-open" : ""} aria-label="Navegação principal">
          {["dashboard", "processos", "documentos", "alteracoes", "relatorios", "notificacoes"].map(item =>
            <button key={item} aria-current={tab === item ? "page" : undefined}
              aria-describedby={item === "notificacoes" && unread > 0 ? "unread-description" : undefined}
              className={tab === item ? "active" : ""} onClick={() => navigate(item)}>
              {pageLabels[item]}
              {item === "notificacoes" && unread > 0 && <span className="nav-count" aria-hidden="true">{unread}</span>}
            </button>
          )}
          {isAdmin &&
            <>
              <button aria-current={tab === "administracao" ? "page" : undefined} className={tab === "administracao" ? "active" : ""} onClick={() => navigate("administracao")}>
                Administração
              </button>
              <button aria-current={tab === "auditoria" ? "page" : undefined} className={tab === "auditoria" ? "active" : ""} onClick={() => navigate("auditoria")}>
                Registros de Auditoria
              </button>
            </>}
          <button onClick={logout}>Sair</button>
        </nav>
        <span id="unread-description" className="sr-only">{unread} {unread === 1 ? "notificação não lida" : "notificações não lidas"}</span>
        <div className="profile"><span>{session.perfil === "ADMINISTRADOR_DIPAC" ? "AD" : "OP"}</span>
          <small>{rotuloDominio(session.perfil)}</small></div>
      </aside>
      <main id="workspace-content" tabIndex={-1}>
        <header><div><small>Sistema Integrado de Controle de Contratos</small><h1 ref={heading} tabIndex={-1}>{pageLabels[tab]}</h1></div></header>

        {tab === "dashboard" && <Dashboard token={session.token} onProcesses={filters => { setProcessFilterRequest({ filters }); navigate("processos"); }} />}
        {(visited.has("processos") || tab === "processos") && <div hidden={tab !== "processos"}><PageFeedback>{notify => <Processes token={session.token} notify={notify} active={tab === "processos"}
          filterRequest={processFilterRequest}
          onDocuments={(process, target = { tipo: "PROCESSO" }) => { setDocumentContext({ processo: { ...process }, target }); navigate("documentos"); }}
          onAlterations={process => { if (!process.instrumento) return;
            setAlterationContext({ id: process.instrumento.id, numero: process.instrumento.numero, tipo: process.instrumento.tipo, numeroProcesso: process.numero }); navigate("alteracoes"); }}
          notificacaoEmFoco={notificacaoEmFoco}
          onNotificacaoFocada={() => setNotificacaoEmFoco(null)} />}</PageFeedback></div>}
        {(visited.has("documentos") || tab === "documentos") && <div hidden={tab !== "documentos"}><PageFeedback>{notify => <Documents token={session.token} notify={notify} active={tab === "documentos"} processContext={documentContext?.processo ?? null} contextTarget={documentContext?.target} onReturn={() => navigate("processos")} />}</PageFeedback></div>}
        {(visited.has("alteracoes") || tab === "alteracoes") && <div hidden={tab !== "alteracoes"}><PageFeedback>{notify => <Alterations token={session.token} notify={notify} active={tab === "alteracoes"} instrumentContext={alterationContext} />}</PageFeedback></div>}
        {(visited.has("relatorios") || tab === "relatorios") && <div hidden={tab !== "relatorios"}><PageFeedback>{notify => <Reports token={session.token} notify={notify} />}</PageFeedback></div>}
        {tab === "notificacoes" && <PageFeedback>{notify => <Notifications token={session.token} notify={notify} inbox={inbox}
          onVerProcessoAdministrativo={notificacaoId => {
          setNotificacaoEmFoco(notificacaoId);
          navigate("processos");
        }} />}</PageFeedback>}
        {isAdmin && (visited.has("administracao") || tab === "administracao") && <div hidden={tab !== "administracao"}><PageFeedback>{notify => <Administration token={session.token} notify={notify} active={tab === "administracao"} />}</PageFeedback></div>}
        {isAdmin && tab === "auditoria" && <AuditRecords token={session.token} />}

      </main>
    </div>
  );
}
