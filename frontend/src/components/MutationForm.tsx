import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useSessionActions, useSessionDraft } from "./SessionDrafts";
export function MutationForm({ onSubmit, children, formKey, ...props }: {
  onSubmit: (event: FormEvent<HTMLFormElement>) => Promise<unknown>;
  children: ReactNode; className?: string; formKey?: string;
}) {
  const id = useId();
  const key = `form:${formKey ?? id}`;
  const [, setDirty, clearDirty] = useSessionDraft(key, false);
  const actions = useSessionActions();
  const pending = actions.pending.has(key);
  const form = useRef<HTMLFormElement>(null);
  const [failure, setFailure] = useState<{ key: string } | null>(null);
  useEffect(() => {
    if (pending || failure?.key !== key || !form.current?.getClientRects().length) return;
    // Some modal errors belong to the surrounding panel rather than the form.
    const scope = form.current.closest('dialog[open]') ?? form.current;
    const invalid = form.current.querySelector<HTMLElement>('[aria-invalid="true"]');
    const alert = [...scope.querySelectorAll<HTMLElement>('[role="alert"]')]
      .find(element => element.textContent?.trim() && element.getClientRects().length);
    const target = invalid ?? alert;
    if (target) {
      if (target === alert) target.tabIndex = -1;
      target.focus({ preventScroll: true });
      target.scrollIntoView({ block: "nearest" });
    }
    setFailure(null);
  }, [pending, failure, key]);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await actions.run(key, async () => {
      setFailure(null);
      if (await onSubmit(event) === false) setFailure({ key });
      else clearDirty();
    });
  }
  return <form ref={form} {...props} onSubmit={submit} onChange={() => setDirty(true)} onReset={clearDirty} aria-busy={pending}>
    <fieldset className="mutation-fields" disabled={pending}>{children}</fieldset>
    {pending && <p role="status">Salvando… Aguarde a confirmação.</p>}
  </form>;
}
