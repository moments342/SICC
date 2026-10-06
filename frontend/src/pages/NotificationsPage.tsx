import { useState } from "react";
import { request } from "../api";
import { useResource } from "../useResource";
import { ResourceState } from "../components/Feedback";
import { rotuloDominio } from "../domainLabels";
import type { AuthenticatedPageProps, NotificacaoInterna } from "../models";
export function Notifications({ token, notify, onVerProcessoAdministrativo }: AuthenticatedPageProps & {
  onVerProcessoAdministrativo: (notificacaoId: number) => void;
}) {
  const inbox = useResource<NotificacaoInterna[]>("/api/v1/notificacoes", token);
  const items = inbox.data ?? [];
  const [reading, setReading] = useState<number[]>([]);
  async function read(id: number) {
    setReading(ids => [...ids, id]);
    try {
      await request(`/api/v1/notificacoes/${id}/lida`, { method: "PATCH" }, token);
      notify("Notificação marcada como lida.");
      inbox.reload();
    } catch (error) {
      notify(`Não foi possível marcar a notificação como lida. ${(error as Error).message} Tente novamente.`, "error");
    } finally { setReading(ids => ids.filter(value => value !== id)); }
  }
  const contratuais = items.filter(item => item.tipo === "ALERTA_VIGENCIA_CONTRATUAL");
  const ted = items.filter(item => item.tipo === "ALERTA_VIGENCIA_TED");
  const outras = items.filter(item => !item.tipo.startsWith("ALERTA_VIGENCIA_"));
  return <section className="panel notifications-panel"><h2>Caixa de entrada</h2>
    <ResourceState {...inbox} onRetry={inbox.reload} label="as notificações" />
    <NotificationGroup titulo="Alertas de Vigência Contratual" items={contratuais}
      onRead={read} reading={reading} onVerProcessoAdministrativo={onVerProcessoAdministrativo} />
    <NotificationGroup titulo="Alertas de Vigência do TED" items={ted}
      onRead={read} reading={reading} onVerProcessoAdministrativo={onVerProcessoAdministrativo} />
    <NotificationGroup titulo="Outras Notificações Internas" items={outras}
      onRead={read} reading={reading} onVerProcessoAdministrativo={onVerProcessoAdministrativo} />
    {!inbox.loading && !inbox.error && !items.length && <p className="empty">Nenhuma notificação.</p>}</section>;
}

function NotificationGroup({ titulo, items, onRead, reading, onVerProcessoAdministrativo }: {
  titulo: string;
  items: NotificacaoInterna[];
  onRead: (id: number) => Promise<void>;
  reading: number[];
  onVerProcessoAdministrativo: (notificacaoId: number) => void;
}) {
  if (!items.length) return null;
  return <section className="notification-group" aria-label={titulo}><h3>{titulo}</h3>{items.map(notificacao => <article
    className={`notification ${notificacao.lida ? "read" : ""}`} key={notificacao.id}>
    <span aria-hidden="true">{notificacao.lida ? "✓" : "•"}</span><div>
      <strong>{rotuloDominio(notificacao.tipo)}</strong>
      <p>{notificacao.mensagem}</p><small>{new Date(notificacao.criadaEm).toLocaleString("pt-BR")}</small></div>
    <div className="notification-actions">
      {notificacao.lida
        ? <span>Lida</span>
        : <button disabled={reading.includes(notificacao.id)} onClick={() => void onRead(notificacao.id)}>
          {reading.includes(notificacao.id) ? "Marcando…" : "Marcar como lida"}</button>}
      {notificacao.processoId !== null &&
        <button onClick={() => onVerProcessoAdministrativo(notificacao.id)}>
          Ver Processo Administrativo
        </button>}
    </div>
  </article>)}
  </section>;
}
