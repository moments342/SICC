import { MutationForm } from "../MutationForm";
import { Modal } from "../Modal";
import { useSessionActions } from "../SessionDrafts";
import type { Notify } from "../../models";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { request } from "../../api";
import { dataLocalAtual, formatarDataNegocio, formatarMomentoInsercao } from "../../formatters";
import type { HistoricoTramitacao, ProcessoAdministrativo, Setor } from "../../models";

export function ProcessTramitation({ token, notify, processo, setores, onChanged, active, actionContainer, summaryContainer }: {
  token: string; notify: Notify; processo: ProcessoAdministrativo; setores: Setor[]; onChanged: () => void;
  active: boolean;
  actionContainer?: HTMLElement | null;
  summaryContainer?: HTMLElement | null;
}) {
  const [historico, setHistorico] = useState<HistoricoTramitacao | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [open, setOpen] = useState(false);
  const [moveError, setMoveError] = useState("");
  const formKey = `process-movement:${processo.id}`;
  const busy = useSessionActions().pending.has(`form:${formKey}`);
  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true); setError("");
    try {
      setHistorico(await request<HistoricoTramitacao>(`/api/v1/processos/${processo.id}/tramitacao`, { signal }, token));
    } catch (loadError) {
      if ((loadError as Error).name === "AbortError") return;
      setHistorico(null); setError((loadError as Error).message);
    } finally { if (!signal?.aborted) setLoading(false); }
  }, [processo.id, token]);
  useEffect(() => {
    const controller = new AbortController(); void load(controller.signal); return () => controller.abort();
  }, [load]);

  async function move(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    setMoveError("");
    try {
      await request("/api/v1/movimentacoes", { method: "POST", body: JSON.stringify({
        contextoTipo: "FORMALIZACAO", contextoId: processo.id, dataMovimentacao: data.get("data"),
        setorDestinoId: Number(data.get("setor")), observacao: data.get("observacao")
      }) }, token);
      notify("Movimentação registrada sem alterar o histórico."); form.reset(); setOpen(false); await load(); onChanged();
    } catch (moveError) { setMoveError((moveError as Error).message); return false; }
  }

  const action = <button className={processo.instrumento ? "primary" : "secondary-action"} aria-haspopup="dialog" onClick={() => setOpen(true)}>Nova movimentação</button>;
  const summary = historico && !loading && !error ? <div className="tramitacao-metrics process-current-summary">
    <div><small>Setor atual</small><strong>{historico.setorAtual?.sigla ?? "Sem movimentação"}</strong></div>
    <div><small>Permanência atual</small><strong>{historico.permanencias.at(-1)?.aberta
      ? `${historico.permanencias.at(-1)?.diasCorridos} dias no setor atual` : "Sem período aberto"}</strong></div>
  </div> : null;
  return <section className="panel span tramitacao-panel"><div className="panel-title"><h2>Tramitação livre · {processo.numero}</h2>
    {actionContainer ? createPortal(action, actionContainer) : action}</div>
    <p className="muted">Cada movimentação é imutável. Para corrigir uma informação, registre uma nova movimentação explicativa.</p>
    <Modal open={open && active} title="Registrar movimentação do processo" busy={busy} onClose={() => setOpen(false)}
      closeLabel="Fechar movimentação do processo" description={`Processo ${processo.numero}. O registro fará parte do histórico de formalização inicial.`}>
    <MutationForm formKey={formKey} className="stack" onSubmit={move}>
      <div className="feedback-live" role="alert" aria-atomic="true">{moveError && <p className="error">{moveError}</p>}</div>
      <label>Data<input name="data" type="date"
      defaultValue={dataLocalAtual()} max={dataLocalAtual()} required /></label>
      <label>Destino<select name="setor" required><option value="">Selecione</option>{setores.map(setor =>
        <option key={setor.id} value={setor.id}>{setor.sigla} · {setor.nome}</option>)}</select></label>
      <label>Observação<textarea name="observacao" rows={3} required /></label><button className="primary">Registrar</button></MutationForm></Modal>
    {loading && <p className="history-state" role="status">Carregando tramitação…</p>}
    {error && <><p className="history-state error" role="alert">{error}</p><button onClick={() => void load()}>Tentar novamente</button></>}
    {!loading && !error && historico && <>{summaryContainer ? createPortal(summary, summaryContainer) : summary}<div className="timeline-heading"><h3>Linha do tempo</h3><small>Ordem por data de negócio e sequência diária</small></div>
      {historico.movimentacoes.length ? <ol className="timeline">{historico.movimentacoes.map(movimento =>
        <li key={movimento.id}><div className="timeline-marker" /><div>
          <time>{formatarDataNegocio(movimento.dataMovimentacao)} · sequência {movimento.sequenciaDiaria}</time>
          <strong>{movimento.setorDestino.sigla} · {movimento.setorDestino.nome}</strong>
          <p>{movimento.observacao ?? "Sem observação"}</p>
          <small>Registrado por {movimento.autor.nome} em {formatarMomentoInsercao(movimento.inseridoEm)}</small>
        </div></li>)}</ol> : <p className="empty">Nenhuma movimentação registrada.</p>}
      {historico.permanencias.length > 0 && <><h3 className="permanencia-title">Permanência no Setor por passagem</h3>
        <div className="permanencias">{historico.permanencias.map((permanencia, indice) =>
          <div key={`${permanencia.setor.id}-${permanencia.dataChegada}-${indice}`}><strong>{permanencia.setor.sigla}</strong>
            <span>{permanencia.diasCorridos} dias corridos</span><small>{formatarDataNegocio(permanencia.dataChegada)} até {permanencia.aberta
              ? "hoje" : formatarDataNegocio(permanencia.dataSaida!)}</small></div>)}</div></>}
    </>}
  </section>;
}
