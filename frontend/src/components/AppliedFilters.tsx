import { rotuloDominio } from "../domainLabels";

const labels: Record<string, string> = {
  numero: "Número", origem: "Origem", objeto: "Objeto", coordenador: "Coordenador",
  tipo: "Tipo", status: "Status", vigencia: "Contratual ou TED",
  vigenciaContratual: "Vigência contratual", vigenciaTed: "Vigência do TED",
  responsavelId: "Responsável DIPAC", semResponsavel: "Responsável DIPAC",
  acao: "Ação", resultado: "Resultado", usuario: "Usuário", ano: "Ano do relatório", setorId: "Setor atual do processo",
  dataInicial: "Data inicial", dataFinal: "Data final", contexto: "Contexto da tramitação"
};

export function AppliedFilters({ values, onRemove, describe }: {
  values: Record<string, string>; onRemove: (key: string) => void;
  describe?: (key: string, value: string) => string;
}) {
  const entries = Object.entries(values).filter(([, value]) => value);
  if (!entries.length) return null;
  return <div className="applied-filters" role="group" aria-label="Filtros aplicados">
    <span>Filtros aplicados</span>
    {entries.map(([key, value]) => {
      const text = `${labels[key] ?? key}: ${describe?.(key, value) ?? (key === "semResponsavel" ? "Não atribuído" : rotuloDominio(value))}`;
      return <button key={key} type="button" className="secondary-action filter-remove"
        aria-label={`Remover filtro ${text}`} onClick={() => onRemove(key)}>
        {text}<svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5 5 10 10M15 5 5 15" /></svg>
      </button>;
    })}
  </div>;
}

export function resetFilterField(form: HTMLFormElement | null, key: string) {
  const control = form?.elements.namedItem(key);
  if (control instanceof HTMLInputElement || control instanceof HTMLSelectElement) control.value = "";
}
