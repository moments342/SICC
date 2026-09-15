import { useCallback, useEffect, useState } from "react";
import { request } from "../api";
import { rotuloDominio } from "../domainLabels";
import type { NotificacaoInterna } from "../models";
export function Notifications({ token, onVerProcessoAdministrativo }: {
  token: string;
  onVerProcessoAdministrativo: (notificacaoId: number) => void;
}) {
  const [items, setItems] = useState<NotificacaoInterna[]>([]);
  const load = useCallback(() => request<typeof items>("/api/v1/notificacoes", {}, token).then(setItems), [token]);
  useEffect(() => { void load(); }, [load]);
  async function read(id: number) { await request(`/api/v1/notificacoes/${id}/lida`, { method: "PATCH" }, token); await load(); }
  const contratuais = items.filter(item => item.tipo === "ALERTA_VIGENCIA_CONTRATUAL");
  const ted = items.filter(item => item.tipo === "ALERTA_VIGENCIA_TED");
  const outras = items.filter(item => !item.tipo.startsWith("ALERTA_VIGENCIA_"));
  return <section className="panel notifications-panel"><h2>Caixa de entrada</h2>
    <NotificationGroup titulo="Alertas de Vigência Contratual" items={contratuais}
      onRead={read} onVerProcessoAdministrativo={onVerProcessoAdministrativo} />
    <NotificationGroup titulo="Alertas de Vigência do TED" items={ted}
      onRead={read} onVerProcessoAdministrativo={onVerProcessoAdministrativo} />
    <NotificationGroup titulo="Outras Notificações Internas" items={outras}
      onRead={read} onVerProcessoAdministrativo={onVerProcessoAdministrativo} />
    {!items.length && <p className="empty">Nenhuma notificação.</p>}</section>;
}

function NotificationGroup({ titulo, items, onRead, onVerProcessoAdministrativo }: {
  titulo: string;
  items: NotificacaoInterna[];
  onRead: (id: number) => Promise<void>;
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
        : <button onClick={() => void onRead(notificacao.id)}>Marcar como lida</button>}
      {notificacao.processoId !== null &&
        <button onClick={() => onVerProcessoAdministrativo(notificacao.id)}>
          Ver Processo Administrativo
        </button>}
    </div>
  </article>)}
  </section>;
}
