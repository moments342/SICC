import { InstrumentValueInput, normalizeInstrumentValue } from "../InstrumentValueInput";
import { InstrumentValueReadOnly } from "../InstrumentValueReadOnly";
import { MutationForm } from "../MutationForm";
import { Modal } from "../Modal";
import type { Notify } from "../../models";
import { FormEvent, useMemo, useState } from "react";
import { useSessionActions, useSessionDraft } from "../SessionDrafts";
import { request } from "../../api";
import type { CampoInstrumento, OperacaoAlteracao, TipoAlteracao } from "../../domain";
import { opcoesDominio } from "../../domainLabels";
import { rotulosCampo, valorAtualDoInstrumento } from "../../instrumentFields";
import type { AlteracaoContratual, InstrumentoAlteracao } from "../../models";
import { alterationTerms } from "./config";

export function OtherAlterationPanel({ token, notify, instrumento, instrumentoId, catalog, onCreated, active }: {
  token: string; notify: Notify; instrumento: InstrumentoAlteracao | undefined; instrumentoId: number;
  catalog: AlteracaoContratual[]; onCreated: (alteracao: AlteracaoContratual) => void; active: boolean;
}) {
  const context = `other:${instrumentoId}`;
  const [operation, setOperation] = useSessionDraft<OperacaoAlteracao>(`${context}:operation`, "ORIGINAL", false);
  const [type, setType] = useSessionDraft<TipoAlteracao>(`${context}:type`, "APOSTILAMENTO", false);
  const draft = `${context}:${type}:${operation}`;
  const [open, setOpen] = useState(false);
  const [error, setError] = useState({ draft, message: "" });
  const busy = useSessionActions().pending.has(`form:${draft}`);
  const [number, setNumber, clearNumber] = useSessionDraft(`${draft}:number`, "");
  const [reference, setReference, clearReference] = useSessionDraft(`${draft}:reference`, "");
  const [field, setField, clearField] = useSessionDraft<CampoInstrumento>(`${draft}:field`, alterationTerms(type).initialField);
  const [value, setValue, clearValue] = useSessionDraft(`${draft}:value`, "");
  const terms = alterationTerms(type);
  const references = useMemo(() => catalog.filter(item =>
    item.tipo === type && item.estado === "EFETIVADA" && item.operacao !== "CANCELAMENTO"), [catalog, type]);

  function selectType(next: TipoAlteracao) {
    setType(next);
  }
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!instrumento) return;
    setError({ draft, message: "" });
    try {
      const result = await request<AlteracaoContratual>("/api/v1/alteracoes", {
        method: "POST", body: JSON.stringify({
          instrumentoId: instrumento.id, tipo: type, numeroOficial: number, operacao: operation,
          referenciaId: operation === "ORIGINAL" ? null : Number(reference),
          mudancas: operation === "CANCELAMENTO" ? [] : [{
            campo: field, valorAnterior: valorAtualDoInstrumento(instrumento, field), valorNovo: normalizeInstrumentValue(field, value)
          }]
        })
      }, token);
      clearNumber(); clearReference(); clearValue(); clearField(); onCreated(result);
      setOpen(false);
      notify(`${terms.singular} #${result.id} criado.`);
    } catch (error) { setError({ draft, message: (error as Error).message }); return false; }
  }

  return <><button type="button" className="secondary-action" disabled={!instrumento}
    aria-describedby={!instrumento ? "alteration-prerequisite" : undefined} aria-haspopup="dialog" onClick={() => setOpen(true)}>Outras operações contratuais</button>
    <Modal open={open && active} title="Outras operações contratuais" wide busy={busy}
      closeLabel="Fechar outras operações" onClose={() => setOpen(false)}
      description="Registre uma alteração original, retificação ou cancelamento. O histórico efetivado será preservado.">
    <div className="alteracoes-contratuais"><p className="document-context"><strong>{instrumento?.numero ?? "Instrumento não selecionado"}</strong>
      <span>{instrumento ? "O preenchimento é mantido ao fechar esta janela." : "Selecione um Instrumento Contratual na página para concluir o cadastro."}</span></p>
    <div className="feedback-live" role="alert" aria-atomic="true">{error.draft === draft && error.message && <p className="error">{error.message}</p>}</div>
    <MutationForm formKey={draft} className="inline-form other-alteration-form" onSubmit={create}>
      <label>Tipo da alteração<select value={type} onChange={event => selectType(event.target.value as TipoAlteracao)}>
        {opcoesDominio("tipoAlteracao").map(option => <option key={option.codigo} value={option.codigo}>{option.rotulo}</option>)}</select></label>
      <label>Operação da alteração<select value={operation}
        onChange={event => setOperation(event.target.value as OperacaoAlteracao)}>
        {opcoesDominio("operacaoAlteracao").map(option => <option key={option.codigo} value={option.codigo}>{option.rotulo}</option>)}</select></label>
      <label className="field-full">Identificação da outra alteração<input required value={number} onChange={event => setNumber(event.target.value)} /></label>
      {operation !== "ORIGINAL" && <label className="field-full">Alteração de referência<select required value={reference}
        onChange={event => setReference(event.target.value)}><option value="">Selecione</option>
        {references.map(item => <option key={item.id} value={item.id}>{item.numeroOficial} · #{item.id}</option>)}</select></label>}
      {operation !== "CANCELAMENTO" && <>
        <label>Campo da outra alteração<select value={field}
          onChange={event => { setField(event.target.value as CampoInstrumento); setValue(""); }}>
          {terms.fields.map(item => <option key={item} value={item}>{rotulosCampo[item]}</option>)}</select></label>
        <InstrumentValueReadOnly label="Estado atual do campo" campo={field} value={valorAtualDoInstrumento(instrumento, field)} />
        <label className="field-full">Valor proposto na outra alteração<InstrumentValueInput label="Valor proposto na outra alteração" campo={field} value={value} onChange={setValue} /></label>
      </>}
      <button className="primary" disabled={!instrumento}>Criar outra alteração</button>
    </MutationForm></div></Modal></>;
}
