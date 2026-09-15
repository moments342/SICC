import { useCallback, useEffect, useState } from "react";
import {
  persistSession,
  readStoredSession,
  SESSION_REJECTED_EVENT
} from "./session";
import type { Session } from "./session";
import { pageLabels } from "./navigation";
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

export default function App() {
  const [session, setSession] = useState<Session | null>(readStoredSession);
  const [tab, setTab] = useState("dashboard");
  const [notificacaoEmFoco, setNotificacaoEmFoco] = useState<number | null>(null);
  const [message, setMessage] = useState("");

  const saveSession = useCallback((value: Session | null) => {
    setSession(value);
    persistSession(value);
  }, []);

  useEffect(() => {
    const rejectSession = () => saveSession(null);
    window.addEventListener(SESSION_REJECTED_EVENT, rejectSession);
    return () => window.removeEventListener(SESSION_REJECTED_EVENT, rejectSession);
  }, [saveSession]);

  if (!session) return <PublicAccess onLogin={saveSession} />;
  if (session.trocaSenhaObrigatoria) {
    return <PasswordChange session={session} onDone={() => saveSession(null)} />;
  }

  return (
    <div className="shell">
      <aside>
        <div className="brand"><div><strong>SICC</strong><small>DIPAC · UFGD</small></div></div>
        <nav>
          {["dashboard", "processos", "documentos", "alteracoes", "relatorios", "notificacoes"].map(item =>
            <button key={item} className={tab === item ? "active" : ""} onClick={() => setTab(item)}>
              {pageLabels[item]}
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
          <small>{rotuloDominio(session.perfil)}</small></div>
      </aside>
      <main>
        <header><div><small>Sistema Integrado de Controle de Contratos</small><h1>{pageLabels[tab]}</h1></div></header>
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
