import { useEffect, useId, useRef, type ReactNode, type RefObject } from "react";
import { createPortal } from "react-dom";

let openDialogs = 0;
let originalOverflow = "";

/** Native dialog keeps the background inert and confines keyboard focus. Children
 * stay mounted while closed so a draft (including its chosen file) is preserved. */
export function Modal({ open, title, description, onClose, children, footer, wide = false, busy = false,
  closeLabel = "Fechar janela", initialFocus }: {
  open: boolean; title: string; description?: string; onClose: () => void;
  children: ReactNode; footer?: ReactNode; wide?: boolean; busy?: boolean; closeLabel?: string;
  initialFocus?: RefObject<HTMLElement | null>;
}) {
  const id = useId();
  const dialog = useRef<HTMLDialogElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const restoreFocus = useRef<HTMLElement | null>(null);
  const close = useRef(onClose);
  close.current = onClose;

  useEffect(() => {
    const element = dialog.current;
    if (!open || !element) return;
    restoreFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (openDialogs++ === 0) originalOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    element.showModal();
    (initialFocus?.current ?? heading.current)?.focus();
    return () => {
      element.close();
      if (--openDialogs === 0) document.body.style.overflow = originalOverflow;
      const trigger = restoreFocus.current;
      requestAnimationFrame(() => {
        const remaining = [...document.querySelectorAll<HTMLDialogElement>("dialog[open]")].at(-1);
        if (trigger?.isConnected && trigger.getClientRects().length && !trigger.matches(":disabled")
          && (!remaining || remaining.contains(trigger))) trigger.focus({ preventScroll: true });
      });
    };
  }, [open]);

  return createPortal(<dialog ref={dialog} className={`modal${wide ? " modal-wide" : ""}`}
    aria-labelledby={`${id}-title`} aria-describedby={description ? `${id}-description` : undefined}
    onKeyDownCapture={event => {
      if (event.key === "Escape") {
        event.preventDefault(); event.stopPropagation();
        if (!busy) close.current();
      }
      if (event.key === "Tab") {
        const controls = [...event.currentTarget.querySelectorAll<HTMLElement>(
          'button, input, select, textarea, a[href], [tabindex]:not([tabindex="-1"])'
        )].filter(element => !element.matches(":disabled") && element.getClientRects().length);
        const first = controls[0], last = controls.at(-1);
        if (!first) { event.preventDefault(); heading.current?.focus(); }
        else if (event.shiftKey && (document.activeElement === first || document.activeElement === heading.current)) {
          event.preventDefault(); last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault(); first.focus();
        }
      }
    }}
    onCancel={event => { event.preventDefault(); if (!busy) close.current(); }}>
    <div className="modal-heading">
      <div><h2 id={`${id}-title`} ref={heading} tabIndex={-1}>{title}</h2>
        {description && <p id={`${id}-description`} className="muted">{description}</p>}</div>
      <button type="button" className="secondary-action modal-close" aria-label={closeLabel}
        disabled={busy} onClick={() => close.current()}>
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m5 5 14 14M19 5 5 19" /></svg>
      </button>
    </div>
    <div className="modal-content">{children}</div>
    {footer && <div className="modal-footer">{footer}</div>}
  </dialog>, document.body);
}
