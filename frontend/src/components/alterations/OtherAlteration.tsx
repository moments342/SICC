import type { Notify } from "../../models";
import { FormEvent, useMemo } from "react";
import { useSessionDraft } from "../SessionDrafts";
import { request } from "../../api";
import type { CampoInstrumento, OperacaoAlteracao, TipoAlteracao } from "../../domain";
import { opcoesDominio } from "../../domainLabels";
import { rotulosCampo, valorAtualDoInstrumento } from "../../instrumentFields";
import type { AlteracaoContratual, InstrumentoAlteracao } from "../../models";
import { alterationTerms } from "./config";

export function OtherAlterationPanel({ token, notify, instrumento, instrumentoId, catalog, onCreated }: {
  token: string; notify: Notify; instrumento: InstrumentoAlteracao | undefined; instrumentoId: number;
  catalog: AlteracaoContratual[]; onCreated: (alteracao: AlteracaoContratual) => void;
}) {
  const context = `other:${instrumentoId}`;
  const [operation, setOperation] = useSessionDraft<OperacaoAlteracao>(`${context}:operation`, "ORIGINAL", false);
  const [type, setType] = useSessionDraft<TipoAlteracao>(`${context}:type`, "APOSTILAMENTO", false);
  const draft = `${context}:${type}:${operation}`;
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
    try {
      const result = await request<AlteracaoContratual>("/api/v1/alteracoes", {
        method: "POST", body: JSON.stringify({
          instrumentoId: instrumento.id, tipo: type, numeroOficial: number, operacao: operation,
          referenciaId: operation === "ORIGINAL" ? null : Number(reference),
          mudancas: operation === "CANCELAMENTO" ? [] : [{
            campo: field, valorAnterior: valorAtualDoInstrumento(instrumento, field), valorNovo: value
          }]
        })
      }, token);
      clearNumber(); clearReference(); clearValue(); clearField(); onCreated(result);
      notify(`${terms.singular} #${result.id} criado.`);
    } catch (error) { notify((error as Error).message, "error"); }
  }

  return <section className="panel"><h2>Outras operações contratuais</h2>
    <p className="muted">Use este fluxo para Apostilamento, retificação ou cancelamento de uma alteração efetivada.</p>
    <form className="inline-form" onSubmit={create}>
      <label>Tipo da alteração<select value={type} onChange={event => selectType(event.target.value as TipoAlteracao)}>
        {opcoesDominio("tipoAlteracao").map(option => <option key={option.codigo} value={option.codigo}>{option.rotulo}</option>)}</select></label>
      <label>Operação da alteração<select value={operation}
        onChange={event => setOperation(event.target.value as OperacaoAlteracao)}>
        {opcoesDominio("operacaoAlteracao").map(option => <option key={option.codigo} value={option.codigo}>{option.rotulo}</option>)}</select></label>
      <label>Identificação da outra alteração<input required value={number} onChange={event => setNumber(event.target.value)} /></label>
      {operation !== "ORIGINAL" && <label>Alteração de referência<select required value={reference}
        onChange={event => setReference(event.target.value)}><option value="">Selecione</option>
        {references.map(item => <option key={item.id} value={item.id}>{item.numeroOficial} · #{item.id}</option>)}</select></label>}
      {operation !== "CANCELAMENTO" && <>
        <label>Campo da outra alteração<select value={field}
          onChange={event => { setField(event.target.value as CampoInstrumento); setValue(""); }}>
          {terms.fields.map(item => <option key={item} value={item}>{rotulosCampo[item]}</option>)}</select></label>
        <label>Estado atual do campo<input readOnly value={valorAtualDoInstrumento(instrumento, field) ?? "Não informado"} /></label>
        <label>Valor proposto na outra alteração<input required value={value} onChange={event => setValue(event.target.value)} /></label>
      </>}
      <button className="primary" disabled={!instrumento}>Criar outra alteração</button>
    </form></section>;
}
