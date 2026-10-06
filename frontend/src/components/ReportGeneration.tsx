import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

const GenerationContext = createContext<{
  pending: ReadonlyMap<string, string>; revision: number; error: string;
  run: (key: string, label: string, action: () => Promise<void>) => Promise<void>;
} | null>(null);

/** Keep in-flight requests locked even while the user visits another page. */
export function ReportGeneration({ children }: { children: ReactNode }) {
  const active = useRef(new Map<string, string>());
  const [pending, setPending] = useState<ReadonlyMap<string, string>>(new Map());
  const [completion, setCompletion] = useState({ revision: 0, error: "" });
  const run = useCallback(async (key: string, label: string, action: () => Promise<void>) => {
    if (active.current.has(key)) return;
    active.current.set(key, label);
    setPending(new Map(active.current));
    let error = "";
    try { await action(); }
    catch (reason) { error = (reason as Error).message; }
    finally {
      active.current.delete(key);
      setPending(new Map(active.current));
      setCompletion(current => ({ revision: current.revision + 1, error }));
    }
  }, []);
  return <GenerationContext.Provider value={{ pending, ...completion, run }}>{children}</GenerationContext.Provider>;
}

export function useReportGeneration() {
  const context = useContext(GenerationContext);
  if (!context) throw new Error("ReportGeneration is required.");
  return context;
}
