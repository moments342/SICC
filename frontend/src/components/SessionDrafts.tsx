import { createContext, useContext, useEffect, useState, type Dispatch, type ReactNode, type SetStateAction } from "react";

type Entry = { value: unknown; unsaved: boolean };
type Drafts = Record<string, Entry>;
const DraftContext = createContext<{
  drafts: Drafts; setDrafts: Dispatch<SetStateAction<Drafts>>;
} | null>(null);

/** Memory only: the provider is remounted whenever the authenticated session changes. */
export function SessionDrafts({ children }: { children: ReactNode }) {
  const [drafts, setDrafts] = useState<Drafts>({});
  const unsaved = Object.values(drafts).some(entry => entry.unsaved);
  useEffect(() => {
    if (!unsaved) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [unsaved]);
  return <DraftContext.Provider value={{ drafts, setDrafts }}>{children}</DraftContext.Provider>;
}

export function useSessionDraft<T>(key: string, initial: T, unsaved = true):
  [T, Dispatch<SetStateAction<T>>, () => void] {
  const context = useContext(DraftContext);
  if (!context) throw new Error("SessionDrafts is required.");
  const { drafts, setDrafts } = context;
  const submittedEntry = drafts[key];
  const clear = () => setDrafts(current => {
    // A completed request must not erase edits made after that request started,
    // including edits made after navigating away and mounting the form again.
    if (current[key] !== submittedEntry) return current;
    const next = { ...current }; delete next[key]; return next;
  });
  return [key in drafts ? drafts[key].value as T : initial, value => setDrafts(current => {
    const previous = key in current ? current[key].value as T : initial;
    const nextValue = typeof value === "function" ? (value as (previous: T) => T)(previous) : value;
    const next = { ...current };
    if (JSON.stringify(nextValue) === JSON.stringify(initial)) delete next[key];
    else next[key] = { value: nextValue, unsaved };
    return next;
  }), clear];
}

export function useHasUnsavedDrafts() {
  const context = useContext(DraftContext);
  return Object.values(context?.drafts ?? {}).some(entry => entry.unsaved);
}
