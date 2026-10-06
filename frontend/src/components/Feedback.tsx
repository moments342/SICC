import { useCallback, useState, type ReactNode } from "react";
import type { Notify } from "../models";

/** Key this boundary by page and session to discard feedback from departed pages. */
export function PageFeedback({ children }: { children: (notify: Notify) => ReactNode }) {
  const [message, setMessage] = useState<{ text: string; kind: "success" | "error" } | null>(null);
  const notify = useCallback<Notify>((text, kind = "success") => setMessage({ text, kind }), []);
  return <>
    <div className={message ? `toast${message.kind === "error" ? " toast-error" : ""}` : undefined}>
      <div className="feedback-live" role="status" aria-atomic="true">{message?.kind === "success" ? message.text : ""}</div>
      <div className="feedback-live" role="alert" aria-atomic="true">{message?.kind === "error" ? message.text : ""}</div>
      {message && <button type="button" aria-label="Fechar mensagem" onClick={() => setMessage(null)}>Fechar</button>}
    </div>
    {children(notify)}
  </>;
}

export function ResourceState({ loading, error, onRetry, label }: {
  loading: boolean; error: string; onRetry: () => void; label: string;
}) {
  return <>
    <div className="feedback-live" role="status" aria-atomic="true">{loading && <p className="muted">Carregando {label}…</p>}</div>
    <div className="feedback-live" role="alert" aria-atomic="true">{!loading && error &&
      <p className="error">Não foi possível carregar {label}. {error}</p>}</div>
    {!loading && error && <button className="secondary-action" type="button" onClick={onRetry}>Tentar novamente</button>}
  </>;
}
