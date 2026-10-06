import { useEffect, useMemo, useState } from "react";
import { request } from "../api";
import { AlterationDetail, AlterationList, EmptyAlterationDetail } from "../components/alterations/AlterationDetail";
import { AlterationDraftPanel } from "../components/alterations/AlterationDraft";
import { OtherAlterationPanel } from "../components/alterations/OtherAlteration";
import { useSessionDraft } from "../components/SessionDrafts";
import { SeletorInstrumentoAlteracao } from "../components/alterations/SeletorInstrumentoAlteracao";
import { ResourceState } from "../components/Feedback";
import type { TipoAlteracao } from "../domain";
import type {
  AlteracaoContratual, AuthenticatedPageProps, EstadoAtualInstrumento, InstrumentoAlteracao, OpcaoInstrumentoAlteracao, Setor
} from "../models";

export function Alterations({ token, notify }: AuthenticatedPageProps) {
  const [type, setType] = useSessionDraft<TipoAlteracao>("alterations:type", "TERMO_ADITIVO", false);
  const [selectedInstrument, setSelectedInstrument] = useSessionDraft<OpcaoInstrumentoAlteracao | null>("alterations:instrument-option", null, false);
  const [sectors, setSectors] = useState<Setor[]>([]);
  const [instrumentId, setInstrumentId] = useSessionDraft("alterations:instrument", 0, false);
  const [catalogState, setCatalog] = useState<{ instrumentId: number; items: AlteracaoContratual[] }>({ instrumentId, items: [] });
  const catalog = catalogState.instrumentId === instrumentId ? catalogState.items : [];
  const [alterationId, setAlterationId] = useSessionDraft<number | null>(`alterations:selected:${type}:${instrumentId}`, null, false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [instrumentAttempt, setInstrumentAttempt] = useState(0);
  const [instrumentState, setInstrumentState] = useState<{
    id: number; data?: InstrumentoAlteracao; loading: boolean; error: string;
  }>({ id: 0, loading: false, error: "" });
  const instrument = instrumentState.id === instrumentId ? instrumentState.data : undefined;

  useEffect(() => {
    const controller = new AbortController();
    setInstrumentState({ id: instrumentId, loading: instrumentId > 0, error: "" });
    if (instrumentId) void request<InstrumentoAlteracao>(`/api/v1/alteracoes/instrumentos/${instrumentId}`,
      { signal: controller.signal }, token)
      .then(data => { if (!controller.signal.aborted) setInstrumentState({ id: instrumentId, data, loading: false, error: "" }); })
      .catch(error => { if (!controller.signal.aborted) setInstrumentState({ id: instrumentId, loading: false, error: (error as Error).message }); });
    return () => controller.abort();
  }, [instrumentId, token, instrumentAttempt]);

  useEffect(() => {
    const controller = new AbortController();
    void request<Setor[]>("/api/v1/setores", { signal: controller.signal }, token).then(sectorCatalog => {
      if (!controller.signal.aborted) setSectors(sectorCatalog.filter(sector => sector.ativo));
    }).catch(error => {
      if ((error as Error).name !== "AbortError") notify((error as Error).message, "error");
    });
    return () => controller.abort();
  }, [notify, token]);

  useEffect(() => {
    setCatalog({ instrumentId, items: [] });
    if (!instrumentId) return;
    const controller = new AbortController();
    void request<AlteracaoContratual[]>(`/api/v1/alteracoes?instrumentoId=${instrumentId}`,
      { signal: controller.signal }, token)
      .then(items => { if (!controller.signal.aborted) setCatalog({ instrumentId, items }); })
      .catch(error => {
        if ((error as Error).name !== "AbortError") notify((error as Error).message, "error");
      });
    return () => controller.abort();
  }, [instrumentId, notify, refreshKey, token]);

  const alterations = useMemo(() => catalog.filter(item => item.tipo === type), [catalog, type]);
  const alteration = alterations.find(item => item.id === alterationId);

  function selectInstrument(item: OpcaoInstrumentoAlteracao | null) {
    setSelectedInstrument(item);
    setInstrumentId(item?.id ?? 0);
  }
  function selectType(next: TipoAlteracao) {
    setType(next);
  }
  function upsert(updated: AlteracaoContratual, select = false) {
    setCatalog(current => current.instrumentId === updated.instrumentoId
      ? { ...current, items: [updated, ...current.items.filter(item => item.id !== updated.id)] }
      : current);
    if (select && updated.tipo === type) setAlterationId(updated.id);
  }
  function updateInstrument(estado: EstadoAtualInstrumento) {
    setInstrumentState(current => current.id === instrumentId && current.data
      ? { ...current, data: { ...current.data, ...estado } } : current);
  }

  return <div className="stack alteracoes-contratuais">
    <p className="muted">O preenchimento é mantido nesta sessão ao trocar de página, tipo ou instrumento. Salve o rascunho antes de sair ou recarregar.</p>
    <AlterationDraftPanel token={token} notify={notify} tipo={type}
      instrumento={instrument} instrumentoId={instrumentId}
      seletorInstrumento={<><SeletorInstrumentoAlteracao token={token} selecionado={selectedInstrument} onSelecionar={selectInstrument} />
        {instrumentId > 0 && <ResourceState label="dados do instrumento selecionado"
          loading={instrumentState.id !== instrumentId || instrumentState.loading}
          error={instrumentState.id === instrumentId ? instrumentState.error : ""}
          onRetry={() => setInstrumentAttempt(value => value + 1)} />}</>}
      onTypeChange={selectType} onCreated={item => upsert(item, true)} />
    {instrumentId > 0 && <div className="grid two">
      <AlterationList tipo={type} items={alterations} selectedId={alterationId}
        onSelect={setAlterationId} onRefresh={() => setRefreshKey(value => value + 1)} />
      {instrument && alteration
        ? <AlterationDetail key={`${instrumentId}:${alteration.id}`} token={token} notify={notify} tipo={type}
          instrumento={instrument} alteracao={alteration} setores={sectors}
          onUpdated={item => upsert(item)} onInstrumentUpdated={updateInstrument} />
        : <EmptyAlterationDetail tipo={type} />}
    </div>}
    <OtherAlterationPanel key={`other:${instrumentId}`} token={token} notify={notify} instrumento={instrument}
      instrumentoId={instrumentId}
      catalog={catalog} onCreated={item => upsert(item, true)} />
  </div>;
}
