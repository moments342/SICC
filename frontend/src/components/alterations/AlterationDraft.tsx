import type { Notify } from "../../models";
import { FormEvent, type ReactNode } from "react";
import { useSessionDraft } from "../SessionDrafts";
import { request } from "../../api";
import type { CampoInstrumento, TipoAlteracao } from "../../domain";
import { rotulosCampo, valorAtualDoInstrumento } from "../../instrumentFields";
import type { AlteracaoContratual, InstrumentoAlteracao } from "../../models";
import { alterationTerms } from "./config";

type Props = {
  token: string; notify: Notify; tipo: TipoAlteracao;
  instrumento: InstrumentoAlteracao | undefined; instrumentoId: number; seletorInstrumento: ReactNode;
  onTypeChange: (tipo: TipoAlteracao) => void;
  onCreated: (alteracao: AlteracaoContratual) => void;
};

export function AlterationDraftPanel({
  token, notify, tipo, instrumento, instrumentoId, seletorInstrumento, onTypeChange, onCreated
}: Props) {
  const terms = alterationTerms(tipo);
  const context = `new:${tipo}:${instrumentoId}`;
  const [numero, setNumero, clearNumber] = useSessionDraft(`${context}:number`, "");
  const [mudancas, setMudancas, clearChanges] = useSessionDraft<{ campo: CampoInstrumento; valorNovo: string }[]>(`${context}:changes`, [
    { campo: terms.initialField, valorNovo: "" }
  ]);

  function changeField(index: number, campo: CampoInstrumento) {
    setMudancas(items => items.map((item, current) => current === index ? { campo, valorNovo: "" } : item));
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!instrumento) return;
    try {
      const result = await request<AlteracaoContratual>("/api/v1/alteracoes", {
        method: "POST", body: JSON.stringify({
          instrumentoId, tipo, numeroOficial: numero, operacao: "ORIGINAL", referenciaId: null,
          mudancas: mudancas.map(item => ({
            campo: item.campo, valorAnterior: valorAtualDoInstrumento(instrumento, item.campo), valorNovo: item.valorNovo
          }))
        })
      }, token);
      clearNumber(); clearChanges(); onCreated(result);
      notify(`Rascunho #${result.id} criado sem alterar o instrumento vigente.`);
    } catch (error) { notify((error as Error).message, "error"); }
  }

  return <section className="panel"><div className="form-actions" aria-label="Tipo de alteração">
    <button type="button" aria-pressed={tipo === "TERMO_ADITIVO"} className={tipo === "TERMO_ADITIVO" ? "primary" : ""}
      onClick={() => onTypeChange("TERMO_ADITIVO")}>Termos Aditivos</button>
    <button type="button" aria-pressed={tipo === "APOSTILAMENTO"} className={tipo === "APOSTILAMENTO" ? "primary" : ""}
      onClick={() => onTypeChange("APOSTILAMENTO")}>Apostilamentos</button>
  </div><h2>Preparar {terms.singular}</h2>
    <p className="muted">{terms.apostilamento
      ? "O rascunho altera somente dados não contratuais aprovados e mantém intacto o estado vigente do instrumento."
      : "O rascunho registra condições propostas e mantém intacto o estado vigente do instrumento."}</p>
    {seletorInstrumento}
    <form className="stack" onSubmit={create}><div className="inline-form">
      <label>Identificação do {terms.singularLower}<input required value={numero} onChange={event => setNumero(event.target.value)} /></label>
    </div><h3>Mudanças propostas</h3>
      {mudancas.map((mudanca, index) => <div className="inline-form mudanca-alteracao" key={index}>
        <label>{index === 0 ? "Campo da mudança" : `Campo da mudança ${index + 1}`}<select value={mudanca.campo}
          onChange={event => changeField(index, event.target.value as CampoInstrumento)}>{terms.fields.map(campo =>
            <option key={campo} value={campo}>{rotulosCampo[campo]}</option>)}</select></label>
        <label>{index === 0 ? "Valor anterior" : `Valor anterior ${index + 1}`}<input readOnly
          value={valorAtualDoInstrumento(instrumento, mudanca.campo) ?? "Não informado"} /></label>
        <label>{index === 0 ? "Novo valor" : `Novo valor ${index + 1}`}<input required value={mudanca.valorNovo}
          onChange={event => setMudancas(items => items.map((item, current) => current === index
            ? { ...item, valorNovo: event.target.value } : item))} /></label>
        {mudancas.length > 1 && <button type="button"
          onClick={() => setMudancas(items => items.filter((_, current) => current !== index))}>Remover</button>}
      </div>)}
      <div className="form-actions"><button type="button"
        onClick={() => setMudancas(items => [...items, { campo: terms.initialField, valorNovo: "" }])}>Adicionar mudança</button>
        <button className="primary" disabled={!instrumento}>Criar rascunho</button></div>
    </form></section>;
}
