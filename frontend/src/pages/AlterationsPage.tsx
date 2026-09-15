import { useEffect, useMemo, useState } from "react";
import { request, requestAllPages } from "../api";
import { AlterationDetail, AlterationList, EmptyAlterationDetail } from "../components/alterations/AlterationDetail";
import { AlterationDraftPanel } from "../components/alterations/AlterationDraft";
import { OtherAlterationPanel } from "../components/alterations/OtherAlteration";
import type { TipoAlteracao } from "../domain";
import type {
  AlteracaoContratual, AuthenticatedPageProps, EstadoAtualInstrumento, Instrumento, ProcessoAdministrativo, Setor
} from "../models";

export function Alterations({ token, notify }: AuthenticatedPageProps) {
  const [type, setType] = useState<TipoAlteracao>("TERMO_ADITIVO");
  const [instruments, setInstruments] = useState<Instrumento[]>([]);
  const [sectors, setSectors] = useState<Setor[]>([]);
  const [instrumentId, setInstrumentId] = useState(0);
  const [catalog, setCatalog] = useState<AlteracaoContratual[]>([]);
  const [alterationId, setAlterationId] = useState<number | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void Promise.all([
      requestAllPages<ProcessoAdministrativo>("/api/v1/processos", token, controller.signal),
      request<Setor[]>("/api/v1/setores", { signal: controller.signal }, token)
    ]).then(([processes, sectorCatalog]) => {
      setInstruments(processes.flatMap(process => process.instrumento ? [process.instrumento] : []));
      setSectors(sectorCatalog.filter(sector => sector.ativo));
    }).catch(error => {
      if ((error as Error).name !== "AbortError") notify((error as Error).message);
    });
    return () => controller.abort();
  }, [notify, token]);

  useEffect(() => {
    setCatalog([]);
    if (!instrumentId) return;
    const controller = new AbortController();
    void request<AlteracaoContratual[]>(`/api/v1/alteracoes?instrumentoId=${instrumentId}`,
      { signal: controller.signal }, token)
      .then(setCatalog)
      .catch(error => {
        if ((error as Error).name !== "AbortError") notify((error as Error).message);
      });
    return () => controller.abort();
  }, [instrumentId, notify, refreshKey, token]);

  const instrument = instruments.find(item => item.id === instrumentId);
  const alterations = useMemo(() => catalog.filter(item => item.tipo === type), [catalog, type]);
  const alteration = alterations.find(item => item.id === alterationId);

  function selectInstrument(id: number) {
    setInstrumentId(id); setAlterationId(null);
  }
  function selectType(next: TipoAlteracao) {
    setType(next); setAlterationId(null);
  }
  function upsert(updated: AlteracaoContratual, select = false) {
    setCatalog(items => [updated, ...items.filter(item => item.id !== updated.id)]);
    if (select && updated.tipo === type) setAlterationId(updated.id);
  }
  function updateInstrument(estado: EstadoAtualInstrumento) {
    setInstruments(items => items.map(item => item.id === instrumentId ? {
      ...item, objeto: estado.objeto, descricao: estado.descricao ?? undefined, natureza: estado.natureza,
      coordenador: estado.coordenador, participes: estado.participes, valorAtual: estado.valorAtual,
      vigenciaContratualFinal: estado.vigenciaContratualFinal, vigenciaTedFinal: estado.vigenciaTedFinal ?? undefined
    } : item));
  }

  return <div className="stack alteracoes-contratuais">
    <AlterationDraftPanel key={`${type}:${instrumentId}`} token={token} notify={notify} tipo={type}
      instrumentos={instruments} instrumentoId={instrumentId} onInstrumentChange={selectInstrument}
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
      catalog={catalog} onCreated={item => upsert(item, true)} />
  </div>;
}
