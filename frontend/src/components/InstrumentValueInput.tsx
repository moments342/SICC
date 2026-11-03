import type { CampoInstrumento } from "../domain";
import { useId } from "react";
export function InstrumentValueInput({ label, campo, value, onChange }: { label: string; campo: CampoInstrumento; value: string; onChange: (value: string) => void }) {
  const hintId = useId();
  const optional = campo === "DESCRICAO" || campo === "VIGENCIA_TED_FINAL";
  const multiline = ["PARTICIPES", "OBJETO", "DESCRICAO"].includes(campo);
  const date = campo.startsWith("VIGENCIA_");
  const hint = campo === "PARTICIPES" ? "Informe um partícipe por linha."
    : campo === "VALOR_ATUAL" ? "Valor em reais. Exemplo: 1.234,56. Zero é permitido."
    : optional ? "Deixe vazio para remover a informação atual." : "";
  const accessible = { "aria-label": label, "aria-describedby": hint ? hintId : undefined };
  return <>{multiline ? <textarea {...accessible} rows={3} required={!optional} maxLength={campo === "OBJETO" ? 1000 : 2000} value={value} onChange={e => onChange(e.target.value)} />
    : <input {...accessible} type={date ? "date" : "text"} inputMode={campo === "VALOR_ATUAL" ? "decimal" : undefined}
      pattern={campo === "VALOR_ATUAL" ? "(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)(?:,[0-9]{1,2})?|[0-9]+\\.[0-9]{1,2}" : undefined}
      maxLength={campo === "NATUREZA" || campo === "COORDENADOR" ? 150 : undefined}
      required={!optional} value={value} onChange={e => onChange(e.target.value)} />}
    {hint && <small id={hintId}>{hint}</small>}</>;
}
export function normalizeInstrumentValue(campo: CampoInstrumento, value: string) {
  if (campo !== "VALOR_ATUAL") return value;
  const text = value.trim();
  return text.includes(",") ? text.replace(/\./g, "").replace(",", ".")
    : /^[0-9]{1,3}(\.[0-9]{3})+$/.test(text) ? text.replace(/\./g, "") : text;
}
