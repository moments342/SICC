import { rotuloDominio } from "../domainLabels";

export function Badge({ value }: { value: string }) {
  return <span className={`badge ${value.toLowerCase()}`}>{rotuloDominio(value)}</span>;
}

export function Metric({ label, value }: { label: string; value: string | number }) {
  return <article className="metric"><small>{label}</small><strong>{value}</strong></article>;
}
