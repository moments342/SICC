import { CAMPOS_APOSTILAMENTO, CAMPOS_INSTRUMENTO } from "./domain";
import type { CampoInstrumento } from "./domain";
import { formatarDataNegocio, money } from "./formatters";
import type { DadosAtuaisInstrumento, EstadoAtualInstrumento } from "./models";

export const rotulosCampo: Record<CampoInstrumento, string> = {
  OBJETO: "Objeto",
  DESCRICAO: "Descrição",
  NATUREZA: "Natureza",
  COORDENADOR: "Coordenador",
  PARTICIPES: "Partícipes",
  VALOR_ATUAL: "Valor atual",
  VIGENCIA_CONTRATUAL_FINAL: "Vigência contratual final",
  VIGENCIA_TED_FINAL: "Vigência TED final"
};

export const camposTermo: readonly CampoInstrumento[] = CAMPOS_INSTRUMENTO;
export const camposApostilamento: readonly CampoInstrumento[] = CAMPOS_APOSTILAMENTO;

export function valoresAtuaisDoInstrumento(
  dados: DadosAtuaisInstrumento
): Record<CampoInstrumento, string | null> {
  return {
    OBJETO: dados.objeto ?? null,
    DESCRICAO: dados.descricao ?? null,
    NATUREZA: dados.natureza ?? null,
    COORDENADOR: dados.coordenador,
    PARTICIPES: dados.participes?.join("\n") ?? null,
    VALOR_ATUAL: Number(dados.valorAtual).toFixed(2),
    VIGENCIA_CONTRATUAL_FINAL: dados.vigenciaContratualFinal,
    VIGENCIA_TED_FINAL: dados.vigenciaTedFinal ?? null
  };
}

export function valorAtualDoInstrumento(
  instrumento: DadosAtuaisInstrumento | undefined,
  campo: CampoInstrumento
): string | null {
  return instrumento ? valoresAtuaisDoInstrumento(instrumento)[campo] : null;
}

export function valorDoEstadoAtual(estado: EstadoAtualInstrumento, campo: CampoInstrumento): string | null {
  return valoresAtuaisDoInstrumento(estado)[campo];
}

export function formatarEfeito(campo: CampoInstrumento, valor: string | null): string {
  if (valor === null || valor === "") return "Não informado";
  if (campo === "VALOR_ATUAL") return money(Number(valor));
  if (campo === "VIGENCIA_CONTRATUAL_FINAL" || campo === "VIGENCIA_TED_FINAL") {
    return formatarDataNegocio(valor);
  }
  return valor;
}
