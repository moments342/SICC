import { useEffect, useRef, useState } from "react";
import { request } from "../api";
import { ProcessCatalogPanel, ProcessCreationPanel } from "../components/processes/ProcessCatalog";
import {
  InactiveProcessSummary,
  InstrumentFormalizationPanel,
  InstrumentSummary,
  ProcessAdministrationPanel
} from "../components/processes/ProcessDetails";
import { ProcessTramitation } from "../components/processes/ProcessTramitation";
import { Modal } from "../components/Modal";
import { ResourceState } from "../components/Feedback";
import { useResource } from "../useResource";
import { useSessionActions } from "../components/SessionDrafts";
import { useRouteState } from "../useRouteState";
import type { ProcessFilterRequest } from "../components/processes/ProcessCatalog";
import type { AuthenticatedPageProps, ProcessoAdministrativo, ProcessDocumentTarget, ResponsavelProcesso, Setor } from "../models";

export function Processes({ token, notify, notificacaoEmFoco, onNotificacaoFocada, active, onDocuments, onAlterations, filterRequest }: AuthenticatedPageProps & {
  active: boolean;
  onDocuments: (processo: ProcessoAdministrativo, target?: ProcessDocumentTarget) => void;
  onAlterations: (processo: ProcessoAdministrativo) => void;
  filterRequest?: ProcessFilterRequest | null;
  notificacaoEmFoco: number | null;
  onNotificacaoFocada: () => void;
}) {
  const detailHeading = useRef<HTMLHeadingElement>(null);
  const [route, updateRoute] = useRouteState("processos", ["processo", "pagina", "ordenacao", "numero", "origem", "objeto", "coordenador", "tipo", "status", "vigencia", "vigenciaContratual", "vigenciaTed", "responsavelId"]);
  const [detailState, setDetailState] = useState({ loading: false, error: "" });
  const [detailAttempt, setDetailAttempt] = useState(0);
  const [summaryContainer, setSummaryContainer] = useState<HTMLDivElement | null>(null);
  const catalogAnchor = useRef<HTMLDivElement>(null);
  const [creating, setCreating] = useState(false);
  const creationPending = useSessionActions().pending.has("form:process-create");
  const [showDetail, setShowDetail] = useState(false);
  const [opened, setOpened] = useState<Record<number, ProcessoAdministrativo>>({});
  const [selected, setSelected] = useState<ProcessoAdministrativo | null>(null);
  const [actionsContainer, setActionsContainer] = useState<HTMLDivElement | null>(null);
  const sectorsResource = useResource<Setor[]>("/api/v1/setores", token);
  const ownersResource = useResource<ResponsavelProcesso[]>("/api/v1/processos/responsaveis", token);
  const setores = sectorsResource.data ?? [];
  const responsaveis = ownersResource.data ?? [];
  const [refreshKey, setRefreshKey] = useState(0);
  const wasActive = useRef(active);
  const selectedRef = useRef(selected);
  selectedRef.current = selected;

  useEffect(() => {
    if (!active) return;
    const id = Number(route.processo);
    if (!Number.isSafeInteger(id) || id <= 0) { setShowDetail(false); setDetailState({ loading: false, error: "" }); return; }
    if (selectedRef.current?.id === id) { setShowDetail(true); return; }
    const controller = new AbortController();
    setShowDetail(false); setDetailState({ loading: true, error: "" });
    void request<ProcessoAdministrativo>(`/api/v1/processos/${id}`, { signal: controller.signal }, token).then(record => {
      if (controller.signal.aborted) return;
      if (record.id !== id) throw new Error("O Processo Administrativo solicitado não está disponível.");
      setOpened(current => ({ ...current, [record.id]: record })); setSelected(record); setShowDetail(true);
      setDetailState({ loading: false, error: "" });
    }).catch(error => { if (!controller.signal.aborted) setDetailState({ loading: false, error: (error as Error).message }); });
    return () => controller.abort();
  }, [route.processo, active, token, detailAttempt]);

  useEffect(() => {
    const returning = active && !wasActive.current;
    wasActive.current = active;
    if (!returning) return;
    setRefreshKey(value => value + 1);
    const record = selectedRef.current;
    if (!record) return;
    const controller = new AbortController();
    void request<ProcessoAdministrativo>(`/api/v1/processos/${record.id}`, { signal: controller.signal }, token)
      .then(updated => {
        if (updated.id !== record.id) throw new Error("Resposta inválida ao consultar o processo.");
        if (!controller.signal.aborted) setOpened(current => ({ ...current, [updated.id]: updated }));
      })
      .catch(error => { if (!controller.signal.aborted) notify(`Não foi possível atualizar a consulta do processo. ${(error as Error).message}`, "error"); });
    return () => controller.abort();
  }, [active, notify, token]);

  useEffect(() => {
    if (notificacaoEmFoco === null) return;
    const controller = new AbortController();
    void request<ProcessoAdministrativo>(`/api/v1/notificacoes/${notificacaoEmFoco}/processo`,
      { signal: controller.signal }, token)
      .then(select)
      .catch(error => { if ((error as Error).name !== "AbortError") notify((error as Error).message, "error"); })
      .finally(() => { if (!controller.signal.aborted) onNotificacaoFocada(); });
    return () => controller.abort();
  }, [notificacaoEmFoco, notify, onNotificacaoFocada, token]);

  useEffect(() => { if (selected && showDetail) detailHeading.current?.focus(); }, [selected?.id, showDetail]);
  useEffect(() => { if (filterRequest) setShowDetail(false); }, [filterRequest]);
  function select(processo: ProcessoAdministrativo) { setOpened(current => ({...current, [processo.id]: processo})); setSelected(processo); setShowDetail(true); updateRoute({ processo: String(processo.id) }); }
  const changed = () => setRefreshKey(value => value + 1);
  function updateProcess(updated: ProcessoAdministrativo | null) {
    if (!updated) { setShowDetail(false); return; }
    const formalized = !opened[updated.id]?.instrumento && !!updated.instrumento;
    setOpened(current => ({ ...current, [updated.id]: updated }));
    if (formalized) {
      requestAnimationFrame(() => {
        if (selectedRef.current?.id === updated.id && detailHeading.current?.getClientRects().length) detailHeading.current.focus();
      });
    }
  }

  const current = selected ? opened[selected.id] ?? selected : null;
  return <div className="grid process-layout">
    {(detailState.loading || detailState.error) && <section className="panel span"><ResourceState {...detailState}
      label="o Processo Administrativo do link" onRetry={() => setDetailAttempt(value => value + 1)} /></section>}
    <Modal open={creating && active && !showDetail} title="Novo Processo Administrativo"
      description="Cadastre os dados administrativos. O preenchimento é mantido ao fechar esta janela."
      busy={creationPending} closeLabel="Fechar novo processo" onClose={() => setCreating(false)}>
      {(ownersResource.loading || ownersResource.error) && <ResourceState {...ownersResource} onRetry={ownersResource.reload} label="os responsáveis DIPAC" />}
      <ProcessCreationPanel embedded token={token} notify={notify} responsaveis={responsaveis}
        onCreated={() => { changed(); setCreating(false); }} />
    </Modal>
    <div ref={catalogAnchor} hidden={showDetail}><ProcessCatalogPanel token={token} notify={notify} selectedId={selected?.id}
      refreshKey={refreshKey} onSelect={select} onCreate={() => setCreating(true)} creating={creating} filterRequest={filterRequest} responsaveis={responsaveis} /></div>
    {current && showDetail && <section className="panel process-detail-heading"><button className="secondary-action" onClick={() => { setShowDetail(false); updateRoute({ processo: "" }); requestAnimationFrame(() => catalogAnchor.current?.querySelector<HTMLButtonElement>('[aria-pressed="true"]')?.focus()); }}>Voltar à lista</button><h2 ref={detailHeading} tabIndex={-1}>Processo Administrativo · {current.numero}</h2>
      {(sectorsResource.loading || ownersResource.loading || sectorsResource.error || ownersResource.error) && <ResourceState loading={sectorsResource.loading || ownersResource.loading}
        error={sectorsResource.error || ownersResource.error} label="os setores e responsáveis DIPAC"
        onRetry={() => { sectorsResource.reload(); ownersResource.reload(); }} />}
      <div className="process-actions" ref={setActionsContainer} aria-label="Ações do Processo Administrativo" />
      <div ref={setSummaryContainer} />
      <div className="form-actions process-related-actions"><button className="secondary-action" onClick={() => onDocuments(current)}>Documentos deste processo</button>
        {current.ativo && current.instrumento && <button className="secondary-action" onClick={() => onAlterations(current)}>Alterações deste instrumento</button>}</div>
    </section>}
    {Object.values(opened).map(record => <div key={record.id} className="stack" hidden={!showDetail || selected?.id !== record.id}>
    {record && !record.ativo && <InactiveProcessSummary processo={record} />}
    {record.instrumento && <InstrumentSummary instrumento={record.instrumento} token={token} notify={notify}
      onDocuments={() => onDocuments(record, { tipo: "INSTRUMENTO" })} />}
    {record?.ativo && <ProcessTramitation key={`tram-${record.id}`} token={token} notify={notify}
      summaryContainer={showDetail && selected?.id === record.id ? summaryContainer : null}
      actionContainer={showDetail && selected?.id === record.id ? actionsContainer : null}
      active={active && showDetail && selected?.id === record.id} processo={record} setores={setores} onChanged={changed} />}
    {record?.ativo && <ProcessAdministrationPanel key={`edit-${record.id}`} token={token} notify={notify}
      actionContainer={showDetail && selected?.id === record.id ? actionsContainer : null}
      active={active && showDetail && selected?.id === record.id} processo={record} responsaveis={responsaveis} onUpdated={updateProcess} onChanged={changed} />}
    {record?.ativo && !record.instrumento && <InstrumentFormalizationPanel key={`formalize-${record.id}`}
      actionContainer={showDetail && selected?.id === record.id ? actionsContainer : null}
      active={active && showDetail && selected?.id === record.id} onDocuments={() => onDocuments(record, { tipo: "PROCESSO", anexarAssinado: true })}
      token={token} notify={notify} processo={record} onUpdated={updateProcess} onChanged={changed} />}
    </div>)}
  </div>;
}
