import { useEffect, useState } from "react";
import { request } from "../api";
import { ProcessCatalogPanel, ProcessCreationPanel } from "../components/processes/ProcessCatalog";
import {
  InactiveProcessSummary,
  InstrumentFormalizationPanel,
  InstrumentSummary,
  ProcessAdministrationPanel
} from "../components/processes/ProcessDetails";
import { ProcessTramitation } from "../components/processes/ProcessTramitation";
import type { AuthenticatedPageProps, ProcessoAdministrativo, ResponsavelProcesso, Setor } from "../models";

export function Processes({ token, notify, notificacaoEmFoco, onNotificacaoFocada }: AuthenticatedPageProps & {
  notificacaoEmFoco: number | null;
  onNotificacaoFocada: () => void;
}) {
  const [selected, setSelected] = useState<ProcessoAdministrativo | null>(null);
  const [setores, setSetores] = useState<Setor[]>([]);
  const [responsaveis, setResponsaveis] = useState<ResponsavelProcesso[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void Promise.all([
      request<Setor[]>("/api/v1/setores", { signal: controller.signal }, token),
      request<ResponsavelProcesso[]>("/api/v1/processos/responsaveis", { signal: controller.signal }, token)
    ]).then(([sectors, owners]) => { setSetores(sectors); setResponsaveis(owners); })
      .catch(error => { if ((error as Error).name !== "AbortError") notify((error as Error).message, "error"); });
    return () => controller.abort();
  }, [notify, token]);

  useEffect(() => {
    if (notificacaoEmFoco === null) return;
    const controller = new AbortController();
    void request<ProcessoAdministrativo>(`/api/v1/notificacoes/${notificacaoEmFoco}/processo`,
      { signal: controller.signal }, token)
      .then(setSelected)
      .catch(error => { if ((error as Error).name !== "AbortError") notify((error as Error).message, "error"); })
      .finally(() => { if (!controller.signal.aborted) onNotificacaoFocada(); });
    return () => controller.abort();
  }, [notificacaoEmFoco, notify, onNotificacaoFocada, token]);

  const changed = () => setRefreshKey(value => value + 1);

  return <div className="grid process-layout">
    <ProcessCreationPanel token={token} notify={notify} responsaveis={responsaveis} onCreated={changed} />
    <ProcessCatalogPanel token={token} notify={notify} selectedId={selected?.id}
      refreshKey={refreshKey} onSelect={setSelected} />
    {selected && !selected.ativo && <InactiveProcessSummary processo={selected} />}
    {selected?.ativo && <ProcessTramitation key={`tram-${selected.id}`} token={token} notify={notify}
      processo={selected} setores={setores} onChanged={changed} />}
    {selected?.ativo && <ProcessAdministrationPanel key={`edit-${selected.id}`} token={token} notify={notify}
      processo={selected} responsaveis={responsaveis} onUpdated={setSelected} onChanged={changed} />}
    {selected?.ativo && !selected.instrumento && <InstrumentFormalizationPanel key={`formalize-${selected.id}`}
      token={token} notify={notify} processo={selected} onUpdated={setSelected} onChanged={changed} />}
    {selected?.ativo && selected.instrumento && <InstrumentSummary instrumento={selected.instrumento} />}
  </div>;
}
