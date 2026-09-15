import { FormEvent, useEffect, useState } from "react";
import { request } from "../../api";
import type { CampoInstrumento, TipoAlteracao } from "../../domain";
import { rotuloDominio } from "../../domainLabels";
import { formatarDataNegocio } from "../../formatters";
import {
  camposTermo, formatarEfeito, rotulosCampo, valorAtualDoInstrumento, valorDoEstadoAtual
} from "../../instrumentFields";
import type { AlteracaoContratual, Documento, EstadoAtualInstrumento, Instrumento, Setor } from "../../models";
import { Badge } from "../Presentation";
import { alterationTerms } from "./config";

type WorkflowProps = {
  token: string; notify: (message: string) => void; tipo: TipoAlteracao;
  instrumento: Instrumento; alteracao: AlteracaoContratual;
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
      key={item.id} onClick={() => onSelect(item.id)}><strong>{item.numeroOficial}</strong>
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

function DraftEditor({ token, notify, tipo, instrumento, alteracao, onUpdated }: WorkflowProps) {
  const terms = alterationTerms(tipo);
  const [numero, setNumero] = useState(alteracao.numeroOficial);
  const [mudancas, setMudancas] = useState(alteracao.mudancas.map(item => ({
    campo: item.campo, valorNovo: item.valorNovo ?? ""
  })));
  useEffect(() => {
    setNumero(alteracao.numeroOficial);
    setMudancas(alteracao.mudancas.map(item => ({ campo: item.campo, valorNovo: item.valorNovo ?? "" })));
  }, [alteracao.mudancas, alteracao.numeroOficial]);

  async function update(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      const result = await request<AlteracaoContratual>(`/api/v1/alteracoes/${alteracao.id}`, {
        method: "PUT", body: JSON.stringify({ numeroOficial: numero, mudancas: mudancas.map(item => ({
          campo: item.campo, valorAnterior: valorAtualDoInstrumento(instrumento, item.campo), valorNovo: item.valorNovo
        })) })
      }, token);
      onUpdated(result); notify(`Rascunho do ${terms.singular} atualizado.`);
    } catch (error) { notify((error as Error).message); }
  }

  if (alteracao.estado !== "RASCUNHO") return null;
  return <form className="stack edicao-alteracao" onSubmit={update}>
    <label>Identificação do rascunho<input required value={numero} onChange={event => setNumero(event.target.value)} /></label>
    {mudancas.map((mudanca, index) => <div className="inline-form" key={index}>
      <label>{index === 0 ? "Campo editado" : `Campo ${index + 1} do rascunho`}<select value={mudanca.campo}
        onChange={event => setMudancas(items => items.map((item, current) => current === index
          ? { campo: event.target.value as CampoInstrumento, valorNovo: "" } : item))}>
        {terms.fields.map(campo => <option key={campo} value={campo}>{rotulosCampo[campo]}</option>)}</select></label>
      <label>{index === 0 ? "Valor anterior editado" : `Estado atual do campo ${index + 1}`}<input readOnly
        value={valorAtualDoInstrumento(instrumento, mudanca.campo) ?? "Não informado"} /></label>
      <label>{index === 0 ? "Novo valor editado" : `Conteúdo proposto ${index + 1}`}<input required value={mudanca.valorNovo}
        onChange={event => setMudancas(items => items.map((item, current) => current === index
          ? { ...item, valorNovo: event.target.value } : item))} /></label>
      {mudancas.length > 1 && <button type="button"
        onClick={() => setMudancas(items => items.filter((_, current) => current !== index))}>Remover</button>}
    </div>)}
    <div className="form-actions"><button type="button"
      onClick={() => setMudancas(items => [...items, { campo: terms.initialField, valorNovo: "" }])}>Adicionar mudança ao rascunho</button>
      <button className="primary">Salvar rascunho</button></div>
  </form>;
}

function EffectuationWorkflow({ token, notify, tipo, instrumento, alteracao, onUpdated, onInstrumentUpdated }:
  WorkflowProps & { onInstrumentUpdated: (estado: EstadoAtualInstrumento) => void }) {
  const terms = alterationTerms(tipo);
  const [documents, setDocuments] = useState<Documento[]>([]);
  const [result, setResult] = useState<EstadoAtualInstrumento | null>(null);
  const [date, setDate] = useState("");
  const [order, setOrder] = useState("");

  useEffect(() => {
    const controller = new AbortController(); setDocuments([]); setResult(null); setDate(""); setOrder("");
    void request<Documento[]>(`/api/v1/documentos?proprietarioTipo=${tipo}&proprietarioId=${alteracao.id}`,
      { signal: controller.signal }, token)
      .then(items => setDocuments(items.filter(item => item.ativo && item.categoria === "ASSINADO"
        && item.versoes[0]?.tipoMime === "application/pdf")))
      .catch(() => { if (!controller.signal.aborted) setDocuments([]); });
    return () => controller.abort();
  }, [alteracao.id, tipo, token]);

  function takesPrecedence(campo: CampoInstrumento) {
    if (!date || !order) return true;
    const current = alteracao.estadoAtualInstrumento.precedenciaPorCampo?.[campo];
    return !current || current.dataEfetivacao < date
      || (current.dataEfetivacao === date && current.ordemOficial < Number(order));
  }
  async function effectuate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget);
    try {
      const updated = await request<AlteracaoContratual>(`/api/v1/alteracoes/${alteracao.id}/efetivacao`, {
        method: "POST", body: JSON.stringify({
          dataEfetivacao: data.get("dataEfetivacao"), ordemOficial: Number(data.get("ordemOficial")),
          documentoAssinadoId: Number(data.get("documentoAssinadoId"))
        })
      }, token);
      onUpdated(updated); setResult(updated.estadoAtualInstrumento); onInstrumentUpdated(updated.estadoAtualInstrumento);
      notify(`${terms.singular} efetivado e estado atual recomputado.`);
    } catch (error) { notify((error as Error).message); }
  }

  if (alteracao.estado !== "RASCUNHO" && !result) return null;
  return <>{alteracao.estado === "RASCUNHO" && <section className="stack efetivacao-alteracao" aria-label="Efeitos a confirmar">
    <div><h3>Efeitos a confirmar</h3><p className="muted">Confira o estado vigente e cada valor proposto antes de tornar o {terms.singularLower} oficial.</p></div>
    {alteracao.mudancas.map(mudanca => <div className="efeito-alteracao" key={mudanca.campo}>
      <strong>{rotulosCampo[mudanca.campo]}</strong><span><small>Estado vigente</small>
        {formatarEfeito(mudanca.campo, valorAtualDoInstrumento(instrumento, mudanca.campo))}</span>
      <span><small>{date && order ? "Resultado previsto" : "Efeito proposto"}</small>
        {formatarEfeito(mudanca.campo, takesPrecedence(mudanca.campo)
          ? mudanca.valorNovo ?? null : valorAtualDoInstrumento(instrumento, mudanca.campo))}
        {date && order && !takesPrecedence(mudanca.campo) && <em>Não prevalece sobre a alteração oficial mais recente.</em>}</span>
    </div>)}
    <form className="inline-form" onSubmit={effectuate}>
      <label>Data de efetivação<input name="dataEfetivacao" type="date" required value={date}
        onChange={event => setDate(event.target.value)} /></label>
      <label>Ordem oficial<input name="ordemOficial" type="number" min="1" required value={order}
        onChange={event => setOrder(event.target.value)} /></label>
      <label>PDF assinado<select name="documentoAssinadoId" required defaultValue=""><option value="">Selecione</option>
        {documents.map(document => <option key={document.id} value={document.id}>#{document.id} · {document.titulo}</option>)}</select></label>
      <button className="primary">Confirmar efetivação</button>
    </form>{documents.length === 0 && <p className="muted">Anexe um Documento Assinado em PDF a este {terms.singular} antes da confirmação.</p>}
  </section>}
    {result && <section className="stack estado-resultante" aria-label="Estado resultante">
      <div className="panel-title"><div><h3>Estado resultante</h3><p className="muted">Dados correntes após aplicar a cronologia oficial.</p></div>
        <Badge value={result.statusProcesso} /></div>
      {alteracao.mudancas.map(mudanca => <div className="mudanca-resumo" key={mudanca.campo}>
        <strong>{rotulosCampo[mudanca.campo]}</strong><span>{formatarEfeito(mudanca.campo, valorDoEstadoAtual(result, mudanca.campo))}</span>
      </div>)}
    </section>}
  </>;
}

function AlterationTramitation({ token, notify, tipo, alteracao, setores, onUpdated }: Omit<WorkflowProps, "instrumento"> & {
  setores: Setor[];
}) {
  const terms = alterationTerms(tipo);
  async function move(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    try {
      await request("/api/v1/movimentacoes", { method: "POST", body: JSON.stringify({
        contextoTipo: tipo, contextoId: alteracao.id, setorDestinoId: Number(data.get("setorDestino")),
        dataMovimentacao: data.get("dataMovimentacao"), observacao: data.get("observacao")
      }) }, token);
      const updated = await request<AlteracaoContratual>(`/api/v1/alteracoes/${alteracao.id}`, {}, token);
      onUpdated(updated); notify(`Movimentação do ${terms.singular} registrada.`); form.reset();
    } catch (error) { notify((error as Error).message); }
  }
  return <><hr /><h3>Tramitação própria</h3><div className="tramitacao-metrics"><div><small>Setor atual</small>
    <strong>{alteracao.tramitacao?.setorAtual ? `${alteracao.tramitacao.setorAtual.sigla} · setor atual` : "Ainda não tramitado"}</strong></div>
    <div><small>Movimentações</small><strong>{alteracao.tramitacao?.movimentacoes.length ?? 0}</strong></div></div>
    <form className="inline-form" onSubmit={move}><label>Data da movimentação<input name="dataMovimentacao" type="date" required /></label>
      <label>Setor de destino<select name="setorDestino" required defaultValue=""><option value="">Selecione</option>
        {setores.map(setor => <option key={setor.id} value={setor.id}>{setor.sigla} · {setor.nome}</option>)}</select></label>
      <label>Observação da movimentação<input name="observacao" /></label><button className="primary">Registrar movimentação</button></form>
    <ol className="timeline">{alteracao.tramitacao?.movimentacoes.map(item => <li key={item.id}><span className="timeline-marker" />
      <div><strong>{item.setorDestino.sigla}</strong><time>{formatarDataNegocio(item.dataMovimentacao)} · sequência {item.sequenciaDiaria}</time>
        {item.observacao && <p>{item.observacao}</p>}<small>Registrado por {item.autor.nome}</small></div></li>)}</ol>
  </>;
}

export function AlterationDetail({ token, notify, tipo, instrumento, alteracao, setores, onUpdated, onInstrumentUpdated }:
  WorkflowProps & { setores: Setor[]; onInstrumentUpdated: (estado: EstadoAtualInstrumento) => void }) {
  const terms = alterationTerms(tipo);
  return <section className="panel"><div className="panel-title"><div><h2>{alteracao.numeroOficial}</h2>
    <small>{terms.singular} #{alteracao.id} · {rotuloDominio(alteracao.estado)}</small></div><Badge value={alteracao.estado} /></div>
    <CurrentInstrumentState alteracao={alteracao} /><AlterationChain alteracao={alteracao} />
    <h3>Mudanças do rascunho</h3>{alteracao.mudancas.map(mudanca => <div className="mudanca-resumo" key={mudanca.campo}>
      <strong>{rotulosCampo[mudanca.campo]}</strong><span>{mudanca.valorAnterior ?? "Não informado"} → {mudanca.valorNovo ?? "Não informado"}</span>
    </div>)}
    <DraftEditor token={token} notify={notify} tipo={tipo} instrumento={instrumento} alteracao={alteracao} onUpdated={onUpdated} />
    <EffectuationWorkflow token={token} notify={notify} tipo={tipo} instrumento={instrumento} alteracao={alteracao}
      onUpdated={onUpdated} onInstrumentUpdated={onInstrumentUpdated} />
    <AlterationTramitation token={token} notify={notify} tipo={tipo} alteracao={alteracao} setores={setores} onUpdated={onUpdated} />
  </section>;
}

export function EmptyAlterationDetail({ tipo }: { tipo: TipoAlteracao }) {
  return <section className="panel"><p className="muted">Selecione um {alterationTerms(tipo).singularLower} para editar o rascunho e acompanhar sua tramitação.</p></section>;
}
