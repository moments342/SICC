import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { Modal } from "./Modal";

type Confirmation = { title: string; message: string; confirmLabel: string; destructive?: boolean; details?: ReactNode };
const ConfirmationContext = createContext<((options: Confirmation) => Promise<boolean>) | null>(null);

export function Confirmations({ children }: { children: ReactNode }) {
  const [question, setQuestion] = useState<Confirmation | null>(null);
  const answer = useRef<((confirmed: boolean) => void) | null>(null);
  const cancelButton = useRef<HTMLButtonElement>(null);
  const settle = useCallback((confirmed: boolean) => {
    const resolve = answer.current;
    answer.current = null;
    setQuestion(null);
    resolve?.(confirmed);
  }, []);
  const confirm = useCallback((options: Confirmation) => {
    if (answer.current) return Promise.resolve(false);
    setQuestion(options);
    return new Promise<boolean>(resolve => { answer.current = resolve; });
  }, []);
  useEffect(() => {
    const cancel = () => settle(false);
    window.addEventListener("hashchange", cancel);
    window.addEventListener("popstate", cancel);
    return () => {
      window.removeEventListener("hashchange", cancel);
      window.removeEventListener("popstate", cancel);
      answer.current?.(false);
      answer.current = null;
    };
  }, [settle]);
  return <ConfirmationContext.Provider value={confirm}>
    {children}
    <Modal open={question !== null} title={question?.title ?? "Confirmar ação"} onClose={() => settle(false)}
      closeLabel="Fechar confirmação" initialFocus={cancelButton}
      footer={<div className="confirmation-actions">
        <button ref={cancelButton} type="button" className="secondary-action" onClick={() => settle(false)}>Cancelar</button>
        <button type="button" className={question?.destructive ? "danger-action" : "primary"}
          onClick={() => settle(true)}>{question?.confirmLabel ?? "Confirmar"}</button>
      </div>}>
      <p className="confirmation-message">{question?.message}</p>
      {question?.details}
    </Modal>
  </ConfirmationContext.Provider>;
}

export function useConfirmation() {
  const confirm = useContext(ConfirmationContext);
  if (!confirm) throw new Error("Confirmations is required.");
  return confirm;
}
