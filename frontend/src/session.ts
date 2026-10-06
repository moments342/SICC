export type Session = {
  token: string;
  perfil: "ADMINISTRADOR_DIPAC" | "OPERADOR_DIPAC";
  trocaSenhaObrigatoria: boolean;
};

export const SESSION_REJECTED_EVENT = "sicc:session-rejected";
const SESSION_STORAGE_KEY = "sicc-session";

export function readStoredSession(): Session | null {
  try {
    const serialized = localStorage.getItem(SESSION_STORAGE_KEY);
    if (!serialized) return null;
    const candidate: unknown = JSON.parse(serialized);
    if (isSession(candidate)) return candidate;
  } catch {
    // Uma entrada corrompida não pode impedir o acesso público.
  }
  clearStoredSession();
  return null;
}

export function persistSession(session: Session | null) {
  try {
    if (session) localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    else localStorage.removeItem(SESSION_STORAGE_KEY);
  } catch {
    // A sessão atual continua utilizável mesmo se o navegador bloquear persistência local.
  }
}

export function rejectStoredSession(token: string) {
  // The app checks which session owns the response before clearing current state.
  window.dispatchEvent(new CustomEvent(SESSION_REJECTED_EVENT, { detail: token }));
}

function clearStoredSession() {
  try {
    localStorage.removeItem(SESSION_STORAGE_KEY);
  } catch {
    // Não há estado local adicional que possa ser recuperado nesta origem.
  }
}

function isSession(value: unknown): value is Session {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Partial<Session>;
  return typeof candidate.token === "string"
    && candidate.token.trim().length > 0
    && (candidate.perfil === "ADMINISTRADOR_DIPAC" || candidate.perfil === "OPERADOR_DIPAC")
    && typeof candidate.trocaSenhaObrigatoria === "boolean";
}
