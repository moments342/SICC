import { InstrumentValueInput, normalizeInstrumentValue } from "../InstrumentValueInput";
import { InstrumentValueReadOnly } from "../InstrumentValueReadOnly";
import { MutationForm } from "../MutationForm";
import { Modal } from "../Modal";
import type { Notify } from "../../models";
import { FormEvent, useRef, useState } from "react";
import { useSessionActions, useSessionDraft } from "../SessionDrafts";
import { request } from "../../api";
import type { CampoInstrumento, TipoAlteracao } from "../../domain";
import { rotulosCampo, valorAtualDoInstrumento } from "../../instrumentFields";
import type { AlteracaoContratual, InstrumentoAlteracao } from "../../models";
import { alterationTerms } from "./config";

type Props = {
  token: string; notify: Notify; tipo: TipoAlteracao;
  instrumento: InstrumentoAlteracao | undefined; instrumentoId: number; active: boolean;
  onCreated: (alteracao: AlteracaoContratual) => void;
};

export function AlterationDraftPanel({
  token, notify, tipo, instrumento, instrumentoId, active, onCreated
}: Props) {
  const terms = alterationTerms(tipo);
  const context = `new:${tipo}:${instrumentoId}`;
  const currentContext = useRef(context); currentContext.current = context;
  const [open, setOpen] = useState(false);
  const [error, setError] = useState({ context, message: "" });
  const busy = useSessionActions().pending.has(`form:${context}`);
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
    setError({ context, message: "" });
    try {
      const result = await request<AlteracaoContratual>("/api/v1/alteracoes", {
        method: "POST", body: JSON.stringify({
          instrumentoId, tipo, numeroOficial: numero, operacao: "ORIGINAL", referenciaId: null,
          mudancas: mudancas.map(item => ({
            campo: item.campo, valorAnterior: valorAtualDoInstrumento(instrumento, item.campo), valorNovo: normalizeInstrumentValue(item.campo, item.valorNovo)
          }))
        })
      }, token);
      clearNumber(); clearChanges(); onCreated(result);
      if (currentContext.current === context) setOpen(false);
      notify(`Rascunho #${result.id} criado sem alterar o instrumento vigente.`);
    } catch (error) { setError({ context, message: (error as Error).message }); return false; }
  }

  return <><button type="button" className="primary" disabled={!instrumento} aria-describedby={!instrumento ? "alteration-prerequisite" : undefined}
    aria-haspopup="dialog" onClick={() => setOpen(true)}>Preparar {terms.singular}</button>
    <Modal open={open && active} wide title={`Preparar ${terms.singular}`} busy={busy}
      closeLabel="Fechar preparação de alteração" onClose={() => setOpen(false)}
      description={terms.apostilamento
      ? "O rascunho altera somente dados não contratuais aprovados e mantém intacto o estado vigente do instrumento."
      : "O rascunho registra condições propostas e mantém intacto o estado vigente do instrumento."}>
    <div className="alteracoes-contratuais"><p className="document-context"><strong>{instrumento?.numero ?? "Instrumento não selecionado"}</strong>
      <span>{instrumento ? "O preenchimento é mantido ao fechar esta janela." : "Selecione um Instrumento Contratual na página para concluir o cadastro."}</span></p>
    <MutationForm formKey={context} className="stack" onSubmit={create}>
      <div className="feedback-live" role="alert" aria-atomic="true">{error.context === context && error.message && <p className="error">{error.message}</p>}</div><div className="inline-form">
      <label>Identificação do {terms.singularLower}<input required placeholder={terms.apostilamento ? "Ex.: AP-01/2026" : "Ex.: TA-01/2026"} value={numero} onChange={event => setNumero(event.target.value)} /></label>
    </div><h3>Mudanças propostas</h3>
      {mudancas.map((mudanca, index) => <div className="inline-form mudanca-alteracao" key={index}>
        <label>{index === 0 ? "Campo da mudança" : `Campo da mudança ${index + 1}`}<select value={mudanca.campo}
          onChange={event => changeField(index, event.target.value as CampoInstrumento)}>{terms.fields.map(campo =>
            <option key={campo} value={campo}>{rotulosCampo[campo]}</option>)}</select></label>
        <InstrumentValueReadOnly label={index === 0 ? "Valor anterior" : `Valor anterior ${index + 1}`} campo={mudanca.campo}
          value={valorAtualDoInstrumento(instrumento, mudanca.campo)} />
        <label className="field-full">{index === 0 ? "Novo valor" : `Novo valor ${index + 1}`}<InstrumentValueInput label={index === 0 ? "Novo valor" : `Novo valor ${index + 1}`} campo={mudanca.campo} value={mudanca.valorNovo}
          onChange={value => setMudancas(items => items.map((item, current) => current === index
            ? { ...item, valorNovo: value } : item))} /></label>
        {mudancas.length > 1 && <button type="button"
          onClick={() => setMudancas(items => items.filter((_, current) => current !== index))}>Remover</button>}
      </div>)}
      <div className="form-actions"><button type="button"
        onClick={() => setMudancas(items => [...items, { campo: terms.initialField, valorNovo: "" }])}>Adicionar mudança</button>
        <button className="primary" disabled={!instrumento}>Criar rascunho</button></div>
    </MutationForm></div></Modal></>;
}
