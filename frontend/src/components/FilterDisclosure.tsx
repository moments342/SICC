import type { ReactNode } from "react";

/** Fields stay mounted so closing the filters never discards a pending search. */
export function FilterDisclosure({ children, count = 0, label = "Mais filtros" }: {
  children: ReactNode; count?: number; label?: string;
}) {
  return <details className="filter-disclosure"><summary>{label}{count > 0 && ` · ${count} ${count === 1 ? "aplicado" : "aplicados"}`}</summary>
    <div className="inline-form filter-fields">{children}</div>
  </details>;
}
