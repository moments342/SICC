import { useEffect, useMemo, useRef, useState } from "react";
import { request } from "../api";
import { AlterationDetail, AlterationList, EmptyAlterationDetail } from "../components/alterations/AlterationDetail";
import { AlterationDraftPanel } from "../components/alterations/AlterationDraft";
import { OtherAlterationPanel } from "../components/alterations/OtherAlteration";
import { useSessionDraft } from "../components/SessionDrafts";
import { SeletorInstrumentoAlteracao } from "../components/alterations/SeletorInstrumentoAlteracao";
import { ResourceState } from "../components/Feedback";
import { alterationTerms } from "../components/alterations/config";
import { rotuloDominio } from "../domainLabels";
import { useRouteState } from "../useRouteState";
import type { TipoAlteracao } from "../domain";
import type {
  AlteracaoContratual, AuthenticatedPageProps, EstadoAtualInstrumento, InstrumentoAlteracao, OpcaoInstrumentoAlteracao, Setor
} from "../models";

export function Alterations({ token, notify, active, instrumentContext }: AuthenticatedPageProps & { active: boolean; instrumentContext?: OpcaoInstrumentoAlteracao | null }) {
  const [route, updateRoute] = useRouteState("alteracoes", ["instrumento", "tipo", "alteracao"]);
  const [pickerOpen, setPickerOpen] = useState(true);
  const pickerToggle = useRef<HTMLButtonElement>(null);
  const restorePickerFocus = useRef(false);
  const [type, setType] = useSessionDraft<TipoAlteracao>("alterations:type", "TERMO_ADITIVO", false);
  const [selectedInstrument, setSelectedInstrument] = useSessionDraft<OpcaoInstrumentoAlteracao | null>("alterations:instrument-option", null, false);
  const [sectors, setSectors] = useState<Setor[]>([]);
  const [instrumentId, setInstrumentId] = useSessionDraft("alterations:instrument", 0, false);
  const [catalogState, setCatalog] = useState<{ instrumentId: number; items: AlteracaoContratual[] }>({ instrumentId, items: [] });
  const catalog = catalogState.instrumentId === instrumentId ? catalogState.items : [];
  const alterationId = Number(route.alteracao) || null;
  const setAlterationId = (id: number | null) => updateRoute({ alteracao: id ? String(id) : "" });
  const [catalogError, setCatalogError] = useState("");
  const [catalogLoading, setCatalogLoading] = useState(Boolean(instrumentId));
  const [refreshKey, setRefreshKey] = useState(0);
  const [instrumentAttempt, setInstrumentAttempt] = useState(0);
  const [instrumentState, setInstrumentState] = useState<{
    id: number; data?: InstrumentoAlteracao; loading: boolean; error: string;
  }>({ id: 0, loading: false, error: "" });
  const instrument = instrumentState.id === instrumentId ? instrumentState.data : undefined;
  useEffect(() => {
    if (!active) return;
    const id = Number(route.instrumento) || 0;
    if (Number.isSafeInteger(id) && id >= 0 && id !== instrumentId) {
      setInstrumentId(id); setSelectedInstrument(null); setPickerOpen(id === 0);
    }
    setType(route.tipo === "APOSTILAMENTO" ? "APOSTILAMENTO" : "TERMO_ADITIVO");
  }, [route.instrumento, route.tipo, active]);
  useEffect(() => {
    if (instrument) { setSelectedInstrument({ id: instrument.id, numero: instrument.numero, tipo: instrument.tipo, numeroProcesso: instrument.numeroProcesso }); }
  }, [instrument]);

  useEffect(() => {
    if (!active) return;
    const controller = new AbortController();
    setInstrumentState({ id: instrumentId, loading: instrumentId > 0, error: "" });
    if (instrumentId) void request<InstrumentoAlteracao>(`/api/v1/alteracoes/instrumentos/${instrumentId}`,
      { signal: controller.signal }, token)
      .then(data => { if (!controller.signal.aborted) setInstrumentState({ id: instrumentId, data, loading: false, error: "" }); })
      .catch(error => { if (!controller.signal.aborted) setInstrumentState({ id: instrumentId, loading: false, error: (error as Error).message }); });
    return () => controller.abort();
  }, [instrumentId, token, instrumentAttempt, active]);

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
    if (!active) return;
    setCatalog({ instrumentId, items: [] });
    setCatalogError(""); setCatalogLoading(Boolean(instrumentId));
    if (!instrumentId) return;
    const controller = new AbortController();
    void request<AlteracaoContratual[]>(`/api/v1/alteracoes?instrumentoId=${instrumentId}`,
      { signal: controller.signal }, token)
      .then(items => { if (!controller.signal.aborted) setCatalog({ instrumentId, items }); })
      .catch(error => {
        if ((error as Error).name !== "AbortError") setCatalogError((error as Error).message);
      }).finally(() => { if (!controller.signal.aborted) setCatalogLoading(false); });
    return () => controller.abort();
  }, [instrumentId, notify, refreshKey, token, active]);

  const alterations = useMemo(() => catalog.filter(item => item.tipo === type), [catalog, type]);
  const alteration = alterations.find(item => item.id === alterationId);

  function selectInstrument(item: OpcaoInstrumentoAlteracao | null) {
    updateRoute({ instrumento: item ? String(item.id) : "", alteracao: "" });
    setSelectedInstrument(item);
    setInstrumentId(item?.id ?? 0);
    if (item) { restorePickerFocus.current = true; setPickerOpen(false); }
  }
  function selectType(next: TipoAlteracao) {
    setType(next);
    updateRoute({ tipo: next, alteracao: "" });
  }
  useEffect(() => {
    if (!pickerOpen && restorePickerFocus.current) {
      restorePickerFocus.current = false;
      pickerToggle.current?.focus({ preventScroll: true });
    }
  }, [pickerOpen, selectedInstrument]);
  useEffect(() => {
    if (!instrumentContext) return;
    setSelectedInstrument(instrumentContext); setInstrumentId(instrumentContext.id); setPickerOpen(false);
    updateRoute({ instrumento: String(instrumentContext.id), alteracao: "" });
  }, [instrumentContext]);
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
    <p className="muted alteration-session-hint">O preenchimento é mantido nesta sessão. Salve o rascunho antes de sair ou recarregar.</p>
    <section className="panel alteration-scope"><h2>Instrumento Contratual</h2>
      {selectedInstrument && !pickerOpen && <p className="document-context"><strong>{selectedInstrument.numero} · {selectedInstrument.tipo && rotuloDominio(selectedInstrument.tipo)}</strong><span>Processo Administrativo {selectedInstrument.numeroProcesso}</span></p>}
      {selectedInstrument && <button ref={pickerToggle} className="secondary-action" aria-expanded={pickerOpen} onClick={() => setPickerOpen(value => !value)}>{pickerOpen ? "Recolher seleção" : "Trocar instrumento"}</button>}
      <div hidden={!pickerOpen}>
      <SeletorInstrumentoAlteracao token={token} selecionado={selectedInstrument} onSelecionar={selectInstrument} />
      </div>
        {instrumentId > 0 && <ResourceState label="dados do instrumento selecionado"
          loading={instrumentState.id !== instrumentId || instrumentState.loading}
          error={instrumentState.id === instrumentId ? instrumentState.error : ""}
          onRetry={() => setInstrumentAttempt(value => value + 1)} />}
      <fieldset className="alteration-type-picker" aria-describedby="alteration-type-help"><legend>Tipo de alteração</legend>
        <div className="alteration-type-options">
          <button type="button" aria-pressed={type === "TERMO_ADITIVO"}
            className={type === "TERMO_ADITIVO" ? "primary" : "secondary-action"} onClick={() => selectType("TERMO_ADITIVO")}>
            {type === "TERMO_ADITIVO" && <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m4 10 4 4 8-8" /></svg>}Termos Aditivos</button>
          <button type="button" aria-pressed={type === "APOSTILAMENTO"}
            className={type === "APOSTILAMENTO" ? "primary" : "secondary-action"} onClick={() => selectType("APOSTILAMENTO")}>
            {type === "APOSTILAMENTO" && <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m4 10 4 4 8-8" /></svg>}Apostilamentos</button>
        </div>
        <p id="alteration-type-help" className="muted">{type === "TERMO_ADITIVO"
          ? "Termo aditivo: altera condições do instrumento, preservando sua identidade."
          : "Apostilamento: ajusta dados não contratuais previstos no instrumento."}</p>
      </fieldset>
      <div className="form-actions alteration-create-actions">
        <AlterationDraftPanel token={token} notify={notify} tipo={type} active={active}
          instrumento={instrument} instrumentoId={instrumentId} onCreated={item => upsert(item, true)} />
        <OtherAlterationPanel key={`other:${instrumentId}`} token={token} notify={notify} instrumento={instrument}
          instrumentoId={instrumentId} active={active} catalog={catalog} onCreated={item => upsert(item, true)} />
      </div>{!instrument && <p className="muted" id="alteration-prerequisite">Selecione um Instrumento Contratual e aguarde seu carregamento para preparar uma alteração.</p>}</section>
    {!instrumentId && <section className="panel"><h2>{alterationTerms(type).plural} do instrumento</h2>
      <p className="muted">Selecione um Instrumento Contratual para consultar as alterações e acompanhar a tramitação.</p></section>}
    {instrumentId > 0 && <div className="grid two">
      <div><ResourceState loading={catalogLoading || catalogState.instrumentId !== instrumentId} error={catalogError} onRetry={() => setRefreshKey(v => v + 1)} label="as alterações" />
      {!catalogLoading && !catalogError && catalogState.instrumentId === instrumentId && <AlterationList tipo={type} items={alterations} selectedId={alterationId}
        onSelect={setAlterationId} onRefresh={() => setRefreshKey(value => value + 1)} />}</div>
      {instrument && alteration
        ? <AlterationDetail key={`${instrumentId}:${alteration.id}`} token={token} notify={notify} tipo={type} active={active}
          instrumento={instrument} alteracao={alteration} setores={sectors}
          onUpdated={item => upsert(item)} onInstrumentUpdated={updateInstrument} />
        : <EmptyAlterationDetail tipo={type} />}
    </div>}
  </div>;
}
