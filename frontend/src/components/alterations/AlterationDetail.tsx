import { ResourceState } from "../Feedback";
import { InstrumentValueInput, normalizeInstrumentValue } from "../InstrumentValueInput";
import { InstrumentValueReadOnly } from "../InstrumentValueReadOnly";
import { MutationForm } from "../MutationForm";
import { Modal } from "../Modal";
import type { Notify } from "../../models";
import { FormEvent, useEffect, useRef, useState } from "react";
import { request } from "../../api";
import type { CampoInstrumento, TipoAlteracao } from "../../domain";
import { rotuloDominio } from "../../domainLabels";
import { formatarDataNegocio } from "../../formatters";
import {
  camposTermo, formatarEfeito, rotulosCampo, valorAtualDoInstrumento, valorDoEstadoAtual
} from "../../instrumentFields";
import type { AlteracaoContratual, Documento, EstadoAtualInstrumento, InstrumentoAlteracao, Setor } from "../../models";
import { Badge } from "../Presentation";
import { alterationTerms } from "./config";
import { useSessionActions, useSessionDraft } from "../SessionDrafts";

type WorkflowProps = {
  token: string; notify: Notify; tipo: TipoAlteracao; active: boolean;
  instrumento: InstrumentoAlteracao; alteracao: AlteracaoContratual;
  onUpdated: (alteracao: AlteracaoContratual) => void;
};

export function AlterationList({ tipo, items, selectedId, onSelect, onRefresh }: {
  tipo: TipoAlteracao; items: AlteracaoContratual[]; selectedId: number | null;
  onSelect: (id: number) => void; onRefresh: () => void;
}) {
  const terms = alterationTerms(tipo);
  return <section className="panel"><div className="panel-title"><h2>{terms.plural} do instrumento</h2>
    <button onClick={onRefresh}>Atualizar</button></div>
    {items.length === 0 && <p className="muted">Nenhum {terms.singular} preparado.</p>}
    <div className="alteracao-lista">{items.map(item => <button className={item.id === selectedId ? "selected" : ""}
      key={item.id} aria-pressed={item.id === selectedId} onClick={() => onSelect(item.id)}><strong>{item.numeroOficial}</strong>
      <small>#{item.id} · {rotuloDominio(item.estado)}</small></button>)}</div>
  </section>;
}

function CurrentInstrumentState({ alteracao }: { alteracao: AlteracaoContratual }) {
  return <section className="stack estado-resultante" aria-label="Estado atual do Instrumento Contratual">
    <div className="panel-title"><div><h3>Estado atual do Instrumento Contratual</h3>
      <p className="muted">Dados vigentes reconstruídos pela data de efetivação e pela ordem oficial.</p></div>
      <Badge value={alteracao.estadoAtualInstrumento.statusProcesso} /></div>
    {camposTermo.map(campo => <div className="mudanca-resumo" key={campo}><strong>{rotulosCampo[campo]}</strong>
      <span>{formatarEfeito(campo, valorDoEstadoAtual(alteracao.estadoAtualInstrumento, campo))}</span></div>)}
  </section>;
}

function AlterationChain({ alteracao }: { alteracao: AlteracaoContratual }) {
  if (!alteracao.cadeia?.length) return null;
  return <section className="stack" aria-label="Cadeia da alteração"><div><h3>Cadeia da alteração</h3>
    <p className="muted">Origem, retificações e cancelamentos permanecem visíveis sem reescrever o histórico.</p></div>
    <ol className="timeline">{alteracao.cadeia.map(item => <li key={item.id}><span className="timeline-marker" />
      <div><strong>{item.numeroOficial}</strong><time>{rotuloDominio(item.operacao)} · #{item.id}
        {item.referenciaId ? ` · referência #${item.referenciaId}` : " · origem"}
        {item.dataEfetivacao ? ` · ${formatarDataNegocio(item.dataEfetivacao)} · ordem ${item.ordemOficial}` : " · rascunho"}</time>
        <small>{item.produzEfeitoAtual ? "Produz efeito" : "Sem efeito atual"}</small>
        {Object.entries(item.valoresProduzidos).map(([campo, valor]) => <p key={campo}>
          {item.operacao === "CANCELAMENTO" ? "Valor restaurado" : "Valor produzido"}: {formatarEfeito(campo as CampoInstrumento, valor)}
        </p>)}</div></li>)}</ol></section>;
}

function DraftEditor({ token, notify, tipo, instrumento, alteracao, onUpdated, active }: WorkflowProps) {
  const terms = alterationTerms(tipo);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState("");
  const busy = useSessionActions().pending.has(`form:edit:${alteracao.id}`);
  const [numero, setNumero, clearNumber] = useSessionDraft(`edit:${alteracao.id}:number`, alteracao.numeroOficial);
  const [mudancas, setMudancas, clearChanges] = useSessionDraft(`edit:${alteracao.id}:changes`, alteracao.mudancas.map(item => ({
    campo: item.campo, valorNovo: item.valorNovo ?? ""
  })));

  async function update(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    try {
      const result = await request<AlteracaoContratual>(`/api/v1/alteracoes/${alteracao.id}`, {
        method: "PUT", body: JSON.stringify({ numeroOficial: numero, mudancas: mudancas.map(item => ({
          campo: item.campo, valorAnterior: valorAtualDoInstrumento(instrumento, item.campo), valorNovo: normalizeInstrumentValue(item.campo, item.valorNovo)
        })) })
      }, token);
      clearNumber(); clearChanges(); onUpdated(result); setOpen(false); notify(`Rascunho do ${terms.singular} atualizado.`);
    } catch (error) { setError((error as Error).message); return false; }
  }

  if (alteracao.estado !== "RASCUNHO") return null;
  return <><button type="button" className="secondary-action" aria-haspopup="dialog" onClick={() => setOpen(true)}>Editar rascunho</button>
    <Modal open={open && active} wide title="Editar rascunho" busy={busy} closeLabel="Fechar edição do rascunho"
      onClose={() => setOpen(false)} description={`${terms.singular} · ${alteracao.numeroOficial}. O preenchimento é mantido ao fechar esta janela.`}>
    <div className="alteracoes-contratuais"><div className="feedback-live" role="alert" aria-atomic="true">{error && <p className="error">{error}</p>}</div>
    <MutationForm formKey={`edit:${alteracao.id}`} className="stack edicao-alteracao" onSubmit={update}>
    <label>Identificação do rascunho<input required value={numero} onChange={event => setNumero(event.target.value)} /></label>
    {mudancas.map((mudanca, index) => <div className="inline-form mudanca-alteracao" key={index}>
      <label>{index === 0 ? "Campo editado" : `Campo ${index + 1} do rascunho`}<select value={mudanca.campo}
        onChange={event => setMudancas(items => items.map((item, current) => current === index
          ? { campo: event.target.value as CampoInstrumento, valorNovo: "" } : item))}>
        {terms.fields.map(campo => <option key={campo} value={campo}>{rotulosCampo[campo]}</option>)}</select></label>
      <InstrumentValueReadOnly label={index === 0 ? "Valor anterior editado" : `Estado atual do campo ${index + 1}`} campo={mudanca.campo}
        value={valorAtualDoInstrumento(instrumento, mudanca.campo)} />
      <label className="field-full">{index === 0 ? "Novo valor editado" : `Conteúdo proposto ${index + 1}`}<InstrumentValueInput label={index === 0 ? "Novo valor editado" : `Conteúdo proposto ${index + 1}`} campo={mudanca.campo} value={mudanca.valorNovo}
        onChange={value => setMudancas(items => items.map((item, current) => current === index
          ? { ...item, valorNovo: value } : item))} /></label>
      {mudancas.length > 1 && <button type="button"
        onClick={() => setMudancas(items => items.filter((_, current) => current !== index))}>Remover</button>}
    </div>)}
    <div className="form-actions"><button type="button"
      onClick={() => setMudancas(items => [...items, { campo: terms.initialField, valorNovo: "" }])}>Adicionar mudança ao rascunho</button>
      <button className="primary">Salvar rascunho</button></div>
  </MutationForm></div></Modal></>;
}

function EffectuationWorkflow({ token, notify, tipo, instrumento, alteracao, onUpdated, onInstrumentUpdated, active }:
  WorkflowProps & { onInstrumentUpdated: (estado: EstadoAtualInstrumento) => void }) {
  const terms = alterationTerms(tipo);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState("");
  const busy = useSessionActions().pending.has(`form:effectuate:${alteracao.id}`);
  const [documentError, setDocumentError] = useState("");
  const [documentLoading, setDocumentLoading] = useState(false);
  const [documentAttempt, setDocumentAttempt] = useState(0);
  const [documents, setDocuments] = useState<Documento[]>([]);
  const [result, setResult] = useState<EstadoAtualInstrumento | null>(null);
  const [date, setDate, clearDate] = useSessionDraft(`effectuate:${alteracao.id}:date`, "");
  const [order, setOrder, clearOrder] = useSessionDraft(`effectuate:${alteracao.id}:order`, "");
  const [documentId, setDocumentId, clearDocument] = useSessionDraft(`effectuate:${alteracao.id}:document`, "");

  useEffect(() => {
    if (!open || !active) return;
    const controller = new AbortController(); setDocuments([]); setResult(null); setDocumentError(""); setDocumentLoading(true);
    void request<Documento[]>(`/api/v1/documentos?proprietarioTipo=${tipo}&proprietarioId=${alteracao.id}`,
      { signal: controller.signal }, token)
      .then(items => { if (!controller.signal.aborted) setDocuments(items.filter(item => item.ativo && item.categoria === "ASSINADO"
        && item.versoes[0]?.tipoMime === "application/pdf")); })
      .catch(error => { if (!controller.signal.aborted) setDocumentError((error as Error).message); })
      .finally(() => { if (!controller.signal.aborted) setDocumentLoading(false); });
    return () => controller.abort();
  }, [alteracao.id, tipo, token, documentAttempt, open, active]);

  function takesPrecedence(campo: CampoInstrumento) {
    if (!date || !order) return true;
    const current = alteracao.estadoAtualInstrumento.precedenciaPorCampo?.[campo];
    return !current || current.dataEfetivacao < date
      || (current.dataEfetivacao === date && current.ordemOficial < Number(order));
  }
  async function effectuate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget);
    setError("");
    try {
      const updated = await request<AlteracaoContratual>(`/api/v1/alteracoes/${alteracao.id}/efetivacao`, {
        method: "POST", body: JSON.stringify({
          dataEfetivacao: data.get("dataEfetivacao"), ordemOficial: Number(data.get("ordemOficial")),
          documentoAssinadoId: Number(data.get("documentoAssinadoId"))
        })
      }, token);
      onUpdated(updated); setResult(updated.estadoAtualInstrumento); onInstrumentUpdated(updated.estadoAtualInstrumento);
      clearDate(); clearOrder(); clearDocument();
      setOpen(false);
      notify(`${terms.singular} efetivado e estado atual recomputado.`);
    } catch (error) { setError((error as Error).message); return false; }
  }

  if (alteracao.estado !== "RASCUNHO" && !result) return null;
  return <>{alteracao.estado === "RASCUNHO" && <button type="button" className="primary" aria-haspopup="dialog"
    onClick={() => setOpen(true)}>Efetivar {terms.singular}</button>}
    <Modal open={open && active && alteracao.estado === "RASCUNHO"} wide title={`Efetivar ${terms.singular}`} busy={busy}
      closeLabel="Fechar efetivação" onClose={() => setOpen(false)}
      description={`${alteracao.numeroOficial} · A efetivação usa as mudanças salvas no rascunho e exige um PDF assinado.`}>
    <div className="alteracoes-contratuais"><div className="feedback-live" role="alert" aria-atomic="true">{error && <p className="error">{error}</p>}</div>
    <section className="stack efetivacao-alteracao" aria-label="Efeitos a confirmar">
    <div><h3>Efeitos a confirmar</h3><p className="muted">Confira o estado vigente e cada valor proposto antes de tornar o {terms.singularLower} oficial.</p></div>
    {alteracao.mudancas.map(mudanca => <div className="efeito-alteracao" key={mudanca.campo}>
      <strong>{rotulosCampo[mudanca.campo]}</strong><span><small>Estado vigente</small>
        {formatarEfeito(mudanca.campo, valorAtualDoInstrumento(instrumento, mudanca.campo))}</span>
      <span><small>{date && order ? "Resultado previsto" : "Efeito proposto"}</small>
        {formatarEfeito(mudanca.campo, takesPrecedence(mudanca.campo)
          ? mudanca.valorNovo ?? null : valorAtualDoInstrumento(instrumento, mudanca.campo))}
        {date && order && !takesPrecedence(mudanca.campo) && <em>Não prevalece sobre a alteração oficial mais recente.</em>}</span>
    </div>)}
    <ResourceState loading={documentLoading} error={documentError} onRetry={() => setDocumentAttempt(v => v + 1)} label="os documentos assinados" />
    {!documentError && <button className="secondary-action" disabled={documentLoading} onClick={() => setDocumentAttempt(v => v + 1)}>Atualizar PDFs assinados</button>}
    <MutationForm formKey={`effectuate:${alteracao.id}`} className="inline-form" onSubmit={effectuate}>
      <label>Data de efetivação<input name="dataEfetivacao" type="date" required value={date}
        onChange={event => setDate(event.target.value)} /></label>
      <label><span id={`official-order-label-${alteracao.id}`}>Ordem oficial</span><input name="ordemOficial" type="number" min="1" required value={order}
        aria-labelledby={`official-order-label-${alteracao.id}`} aria-describedby={`official-order-${alteracao.id}`} onChange={event => setOrder(event.target.value)} />
        <small className="muted" id={`official-order-${alteracao.id}`}>Use a sequência oficial do documento. Na mesma data de efetivação, a maior ordem prevalece para cada campo alterado.</small></label>
      <label>PDF assinado<select name="documentoAssinadoId" required value={documentId}
        onChange={event => setDocumentId(event.target.value)}><option value="">Selecione</option>
        {documents.map(document => <option key={document.id} value={document.id}>#{document.id} · {document.titulo}</option>)}</select></label>
      <button className="primary" disabled={documentLoading || !!documentError || !documents.length}>Confirmar efetivação</button>
    </MutationForm>{!documentLoading && !documentError && documents.length === 0 && <p className="muted">Anexe um Documento Assinado em PDF a este {terms.singular} antes da confirmação.</p>}
  </section></div></Modal>
    {result && <section className="stack estado-resultante" aria-label="Estado resultante">
      <div className="panel-title"><div><h3>Estado resultante</h3><p className="muted">Dados correntes após aplicar a cronologia oficial.</p></div>
        <Badge value={result.statusProcesso} /></div>
      {alteracao.mudancas.map(mudanca => <div className="mudanca-resumo" key={mudanca.campo}>
        <strong>{rotulosCampo[mudanca.campo]}</strong><span>{formatarEfeito(mudanca.campo, valorDoEstadoAtual(result, mudanca.campo))}</span>
      </div>)}
    </section>}
  </>;
}

function AlterationTramitation({ token, notify, tipo, alteracao, setores, onUpdated, active }: Omit<WorkflowProps, "instrumento"> & {
  setores: Setor[];
}) {
  const terms = alterationTerms(tipo);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState("");
  const busy = useSessionActions().pending.has(`form:movement:${alteracao.id}`);
  const [movement, setMovement, clearMovement] = useSessionDraft(`movement:${alteracao.id}`, { date: "", sector: "", note: "" });
  const [refreshError, setRefreshError] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  async function refresh() {
    setRefreshing(true); setRefreshError("");
    try { onUpdated(await request<AlteracaoContratual>(`/api/v1/alteracoes/${alteracao.id}`, {}, token)); }
    catch { setRefreshError("A movimentação foi registrada. Não foi possível atualizar a tramitação; tente consultar novamente."); }
    finally { setRefreshing(false); }
  }
  async function move(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    setError("");
    try {
      await request("/api/v1/movimentacoes", { method: "POST", body: JSON.stringify({
        contextoTipo: tipo, contextoId: alteracao.id, setorDestinoId: Number(data.get("setorDestino")),
        dataMovimentacao: data.get("dataMovimentacao"), observacao: data.get("observacao")
      }) }, token);
      clearMovement(); setOpen(false); notify(`Movimentação do ${terms.singular} registrada.`); await refresh();
    } catch (error) { setError((error as Error).message); return false; }
  }
  return <><hr /><div className="panel-title"><h3>Tramitação própria</h3>
    <button type="button" className="secondary-action" aria-haspopup="dialog" onClick={() => setOpen(true)}>Nova movimentação</button></div>
    <div className="tramitacao-metrics"><div><small>Setor atual</small>
    <strong>{alteracao.tramitacao?.setorAtual ? `${alteracao.tramitacao.setorAtual.sigla} · setor atual` : "Ainda não tramitado"}</strong></div>
    <div><small>Movimentações</small><strong>{alteracao.tramitacao?.movimentacoes.length ?? 0}</strong></div></div>
    <Modal open={open && active} title="Registrar movimentação" busy={busy} onClose={() => setOpen(false)}
      closeLabel="Fechar movimentação" description={`${terms.singular} · ${alteracao.numeroOficial}. O registro será acrescentado ao histórico próprio desta alteração.`}>
    <div className="feedback-live" role="alert" aria-atomic="true">{error && <p className="error">{error}</p>}</div>
    <MutationForm formKey={`movement:${alteracao.id}`} className="stack" onSubmit={move}><label>Data da movimentação<input name="dataMovimentacao" type="date" required
      value={movement.date} onChange={event => setMovement({ ...movement, date: event.target.value })} /></label>
      <label>Setor de destino<select name="setorDestino" required value={movement.sector}
        onChange={event => setMovement({ ...movement, sector: event.target.value })}><option value="">Selecione</option>
        {setores.map(setor => <option key={setor.id} value={setor.id}>{setor.sigla} · {setor.nome}</option>)}</select></label>
      <label>Observação da movimentação<input name="observacao" value={movement.note}
        onChange={event => setMovement({ ...movement, note: event.target.value })} /></label><button className="primary">Registrar movimentação</button></MutationForm></Modal>
    <ResourceState loading={refreshing} error={refreshError} onRetry={() => void refresh()} label="a tramitação atualizada" />
    <ol className="timeline">{alteracao.tramitacao?.movimentacoes.map(item => <li key={item.id}><span className="timeline-marker" />
      <div><strong>{item.setorDestino.sigla}</strong><time>{formatarDataNegocio(item.dataMovimentacao)} · sequência {item.sequenciaDiaria}</time>
        {item.observacao && <p>{item.observacao}</p>}<small>Registrado por {item.autor.nome}</small></div></li>)}</ol>
  </>;
}

export function AlterationDetail({ token, notify, tipo, instrumento, alteracao, setores, onUpdated, onInstrumentUpdated, active }:
  WorkflowProps & { setores: Setor[]; onInstrumentUpdated: (estado: EstadoAtualInstrumento) => void }) {
  const terms = alterationTerms(tipo);
  const heading = useRef<HTMLHeadingElement>(null);
  const previousState = useRef(alteracao.estado);
  useEffect(() => {
    if (previousState.current === "RASCUNHO" && alteracao.estado !== "RASCUNHO" && active) heading.current?.focus();
    previousState.current = alteracao.estado;
  }, [alteracao.estado, active]);
  return <section className="panel alteration-detail"><div className="panel-title"><div><h2 ref={heading} tabIndex={-1}>{alteracao.numeroOficial}</h2>
    <small>{terms.singular} #{alteracao.id} · {rotuloDominio(alteracao.estado)}</small></div><Badge value={alteracao.estado} /></div>
    <div className="form-actions alteration-workflow-actions">
      <DraftEditor token={token} notify={notify} tipo={tipo} instrumento={instrumento} alteracao={alteracao} onUpdated={onUpdated} active={active} />
      <EffectuationWorkflow token={token} notify={notify} tipo={tipo} instrumento={instrumento} alteracao={alteracao}
        onUpdated={onUpdated} onInstrumentUpdated={onInstrumentUpdated} active={active} />
    </div>
    <CurrentInstrumentState alteracao={alteracao} /><AlterationChain alteracao={alteracao} />
    <h3>Mudanças do rascunho</h3>{alteracao.mudancas.map(mudanca => <div className="mudanca-resumo" key={mudanca.campo}>
      <strong>{rotulosCampo[mudanca.campo]}</strong><span>{mudanca.valorAnterior ?? "Não informado"} → {mudanca.valorNovo ?? "Não informado"}</span>
    </div>)}
    <AlterationTramitation token={token} notify={notify} tipo={tipo} alteracao={alteracao} setores={setores} onUpdated={onUpdated} active={active} />
  </section>;
}

export function EmptyAlterationDetail({ tipo }: { tipo: TipoAlteracao }) {
  return <section className="panel"><p className="muted">Selecione um {alterationTerms(tipo).singularLower} para editar o rascunho e acompanhar sua tramitação.</p></section>;
}
