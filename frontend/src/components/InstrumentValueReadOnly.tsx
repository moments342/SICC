import { useId } from "react";
import type { CampoInstrumento } from "../domain";
import { formatarEfeito } from "../instrumentFields";

/** Display formatting is separate from the canonical value sent to the API. */
export function InstrumentValueReadOnly({ label, campo, value }: {
  label: string; campo: CampoInstrumento; value: string | null;
}) {
  const labelId = useId();
  return <div className="instrument-readonly" role="group" aria-labelledby={labelId}>
    <span id={labelId}>{label}</span>
    <p className="instrument-readonly-value">{formatarEfeito(campo, value)}</p>
  </div>;
}
