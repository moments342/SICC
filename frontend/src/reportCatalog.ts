import { FORMATOS_RELATORIO, TIPOS_RELATORIO } from "./domain";
import type { FormatoRelatorio, TipoRelatorio } from "./domain";

const filtrosComuns = ["numero", "origem", "tipo", "status", "vigenciaContratual", "vigenciaTed"] as const;

type DefinicaoRelatorio = {
  rotulo: string;
  filtros: readonly string[];
  formatos: readonly FormatoRelatorio[];
};

const definicoes: Record<TipoRelatorio, DefinicaoRelatorio> = {
  ANUAL_PROCESSOS: {
    rotulo: "Anual de processos", filtros: [...filtrosComuns, "ano"], formatos: FORMATOS_RELATORIO
  },
  INSTRUMENTOS_POR_TIPO: {
    rotulo: "Instrumentos por tipo", filtros: filtrosComuns, formatos: FORMATOS_RELATORIO
  },
  HISTORICO_TRAMITACOES: {
    rotulo: "Histórico de tramitações",
    filtros: [...filtrosComuns, "contexto", "dataInicial", "dataFinal"], formatos: FORMATOS_RELATORIO
  },
  VIGENCIAS: { rotulo: "Vigências", filtros: filtrosComuns, formatos: FORMATOS_RELATORIO },
  CONSOLIDADO: { rotulo: "Consolidado", filtros: filtrosComuns, formatos: FORMATOS_RELATORIO }
};

export const CATALOGO_RELATORIOS = TIPOS_RELATORIO.map(codigo => ({ codigo, ...definicoes[codigo] }));

const rotulosFiltros: Record<string, string> = {
  numero: "Número do processo", origem: "Origem", ano: "Ano de cadastro",
  dataInicial: "Início do período", dataFinal: "Fim do período",
  tipo: "Tipo de instrumento", contexto: "Contexto da tramitação", status: "Status do processo",
  vigenciaContratual: "Situação da vigência contratual", vigenciaTed: "Situação da vigência do TED"
};

export function rotuloFiltroRelatorio(nome: string) {
  return rotulosFiltros[nome] ?? nome;
}

export function filtrosDoRelatorio(tipo: TipoRelatorio, filtros: Record<string, string>) {
  return Object.fromEntries(Object.entries(filtros)
    .filter(([nome, valor]) => valor && definicoes[tipo].filtros.includes(nome)));
}
