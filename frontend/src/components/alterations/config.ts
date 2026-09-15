import type { CampoInstrumento, TipoAlteracao } from "../../domain";
import { camposApostilamento, camposTermo } from "../../instrumentFields";

export function alterationTerms(tipo: TipoAlteracao) {
  const apostilamento = tipo === "APOSTILAMENTO";
  return {
    apostilamento,
    singular: apostilamento ? "Apostilamento" : "Termo Aditivo",
    plural: apostilamento ? "Apostilamentos" : "Termos Aditivos",
    singularLower: apostilamento ? "apostilamento" : "termo",
    initialField: (apostilamento ? "COORDENADOR" : "OBJETO") as CampoInstrumento,
    fields: apostilamento ? camposApostilamento : camposTermo
  };
}
