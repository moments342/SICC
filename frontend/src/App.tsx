import { useCallback, useEffect, useRef, useState } from "react";
import {
  persistSession,
  readStoredSession,
  SESSION_REJECTED_EVENT
} from "./session";
import type { Session } from "./session";
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

export default function App() {
  const [session, setSession] = useState<Session | null>(readStoredSession);

  const saveSession = useCallback((value: Session | null) => {
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}${value ? "#/dashboard" : ""}`);
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

  return <SessionDrafts key={session.token}><ReportGeneration>
    <Workspace session={session} onLogout={() => saveSession(null)} />
  </ReportGeneration></SessionDrafts>;
}

function Workspace({ session, onLogout }: { session: Session; onLogout: () => void }) {
  const [tab, setTab] = useState(() => pageFromLocation(session.perfil));
  const [notificacaoEmFoco, setNotificacaoEmFoco] = useState<number | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const hasUnsaved = useHasUnsavedDrafts();
  const isAdmin = session.perfil === "ADMINISTRADOR_DIPAC";

  function navigate(next: string) {
    const destination = allowedPage(next, session.perfil);
    if (destination === tab) return;
    window.history.pushState(null, "", `#/${destination}`);
    setTab(destination);
  }
  useEffect(() => {
    const restore = () => {
      const destination = pageFromLocation(session.perfil);
      window.history.replaceState(null, "", `#/${destination}`);
      setTab(destination);
    };
    window.addEventListener("popstate", restore);
    window.addEventListener("hashchange", restore);
    return () => {
      window.removeEventListener("popstate", restore);
      window.removeEventListener("hashchange", restore);
    };
  }, [session.perfil]);
  useEffect(() => {
    window.history.replaceState(null, "", `#/${tab}`);
    document.title = `${pageLabels[tab]} · SICC`;
    heading.current?.focus();
  }, [tab]);

  function logout() {
    if (hasUnsaved && !window.confirm("Há preenchimento não salvo em Alterações contratuais. Sair descartará esse preenchimento. Deseja sair?")) return;
    onLogout();
  }

  return (
    <div className="shell">
      <aside>
        <div className="brand"><div><strong>SICC</strong><small>DIPAC · UFGD</small></div></div>
        <nav aria-label="Navegação principal">
          {["dashboard", "processos", "documentos", "alteracoes", "relatorios", "notificacoes"].map(item =>
            <button key={item} aria-current={tab === item ? "page" : undefined}
              className={tab === item ? "active" : ""} onClick={() => navigate(item)}>
              {pageLabels[item]}
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
        <div className="profile"><span>{session.perfil === "ADMINISTRADOR_DIPAC" ? "AD" : "OP"}</span>
          <small>{rotuloDominio(session.perfil)}</small></div>
      </aside>
      <main>
        <header><div><small>Sistema Integrado de Controle de Contratos</small><h1 ref={heading} tabIndex={-1}>{pageLabels[tab]}</h1></div></header>
        <PageFeedback key={`${session.token}:${tab}`}>{notify => <>
        {tab === "dashboard" && <Dashboard token={session.token} />}
        {tab === "processos" && <Processes token={session.token} notify={notify}
          notificacaoEmFoco={notificacaoEmFoco}
          onNotificacaoFocada={() => setNotificacaoEmFoco(null)} />}
        {tab === "documentos" && <Documents token={session.token} notify={notify} />}
        {tab === "alteracoes" && <Alterations token={session.token} notify={notify} />}
        {tab === "relatorios" && <Reports token={session.token} notify={notify} />}
        {tab === "notificacoes" && <Notifications token={session.token} notify={notify}
          onVerProcessoAdministrativo={notificacaoId => {
          setNotificacaoEmFoco(notificacaoId);
          navigate("processos");
        }} />}
        {isAdmin && tab === "administracao" && <Administration token={session.token} notify={notify} />}
        {isAdmin && tab === "auditoria" && <AuditRecords token={session.token} />}
        </>}</PageFeedback>
      </main>
    </div>
  );
}
