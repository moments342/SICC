import { MutationForm } from "../components/MutationForm";
import { Modal } from "../components/Modal";
import { useConfirmation } from "../components/Confirmation";
import { useSessionActions, useSessionDraft } from "../components/SessionDrafts";
import { FormEvent, useState } from "react";
import { request } from "../api";
import { useResource } from "../useResource";
import { usePendingAction } from "../usePendingAction";
import { ResourceState } from "../components/Feedback";
import { Badge } from "../components/Presentation";
import { UserAdministration } from "../components/admin/UserAdministration";
import type { AuthenticatedPageProps, Setor } from "../models";

export function Administration({ token, notify, active }: AuthenticatedPageProps & { active: boolean }) {
  const actions = usePendingAction();
  const confirm = useConfirmation();
  const forms = useSessionActions();
  const [modal, setModal] = useState<"sector" | "edit-sector" | null>(null);
  const [errors, setErrors] = useState({ sector: "", edit: "" });
  const sectorsResource = useResource<Setor[]>("/api/v1/admin/setores", token);
  const setores = sectorsResource.data ?? [];
  const [setorEmEdicao, setSetorEmEdicao] = useState<Setor | null>(null);
  const [sectorDraft, setSectorDraft, clearSectorDraft] = useSessionDraft(`admin:sector:${setorEmEdicao?.id ?? 0}`, {
    sigla: setorEmEdicao?.sigla ?? "", nome: setorEmEdicao?.nome ?? ""
  });
  const editKey = `admin-sector-edit:${setorEmEdicao?.id ?? 0}`;
  const pending = (key: string) => forms.pending.has(`form:${key}`);
  const close = () => setModal(null);

  function extrairIdentidadeSetor(form: HTMLFormElement) {
    const data = new FormData(form);
    return { sigla: String(data.get("sigla")), nome: String(data.get("nome")) };
  }
  async function criarSetor(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget;
    setErrors(current => ({ ...current, sector: "" }));
    try {
      await request("/api/v1/admin/setores", { method: "POST", body: JSON.stringify(extrairIdentidadeSetor(form)) }, token);
      form.reset(); close(); notify("Setor incluído no catálogo."); sectorsResource.reload();
    } catch (error) { setErrors(current => ({ ...current, sector: (error as Error).message })); return false; }
  }
  async function editarSetor(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (!setorEmEdicao) return;
    const identity = extrairIdentidadeSetor(e.currentTarget);
    setErrors(current => ({ ...current, edit: "" }));
    try {
      await request(`/api/v1/admin/setores/${setorEmEdicao.id}`, { method: "PUT", body: JSON.stringify(identity) }, token);
      setSetorEmEdicao({ ...setorEmEdicao, ...identity }); clearSectorDraft(); close();
      notify("Identidade do setor atualizada."); sectorsResource.reload();
    } catch (error) { setErrors(current => ({ ...current, edit: (error as Error).message })); return false; }
  }
  async function toggleSector(id: number, ativo: boolean) {
    if (ativo && !await confirm({ title: "Desativar setor?", message: `${setores.find(sector => sector.id === id)?.sigla ?? "Este setor"} deixará de estar disponível como destino de novas tramitações. Os registros históricos serão preservados.`,
      confirmLabel: "Desativar setor", destructive: true })) return;
    try { await request(`/api/v1/admin/setores/${id}/ativo?ativo=${!ativo}`, { method: "PATCH" }, token);
      notify(`Setor ${ativo ? "desativado" : "reativado"}.`); sectorsResource.reload(); }
    catch (error) { notify((error as Error).message, "error"); return false; }
  }

  return <div className="grid two administration-page">
    <UserAdministration token={token} notify={notify} active={active} />
    <section className="panel catalogo-setores"><div className="admin-panel-heading"><h2>Catálogo de setores</h2>
      <button className="primary admin-create" aria-haspopup="dialog" onClick={() => setModal("sector")}>
        <svg viewBox="0 0 20 20" aria-hidden="true"><path d="M10 4v12M4 10h12" /></svg>Criar setor</button></div>
      <p className="muted">Somente setores ativos ficam disponíveis como novo destino de tramitação.</p>
      <ResourceState {...sectorsResource} onRetry={sectorsResource.reload} label="os setores" />
      <div className="lista-setores">{setores.map(s => <article className={`doc linha-setor ${s.ativo ? "ativo" : "inativo"}`} key={s.id}>
        <div><strong>{s.sigla}</strong><small>{s.nome}</small></div><Badge value={s.ativo ? "ATIVO" : "INATIVO"} />
        <div className="acoes-setor"><button className="secondary-action" aria-label={`Editar setor ${s.sigla}`} aria-haspopup="dialog"
          onClick={() => { if (s.id !== setorEmEdicao?.id) setErrors(current => ({ ...current, edit: "" })); setSetorEmEdicao(s); setModal("edit-sector"); }}>Editar</button>
          <button disabled={actions.pending.has(`sector:${s.id}`)} onClick={() => void actions.run(`sector:${s.id}`, () => toggleSector(s.id, s.ativo))}>
            {s.ativo ? "Desativar" : "Reativar"}</button></div>
      </article>)}
      {!sectorsResource.loading && !sectorsResource.error && !setores.length && <p className="empty">Nenhum setor cadastrado.</p>}</div>
    </section>

    <Modal open={modal === "sector" && active} title="Criar setor" onClose={close} closeLabel="Fechar criação de setor"
      description="O setor ficará disponível como destino de novas tramitações." busy={pending("admin-sector-create")}>
      <MutationForm formKey="admin-sector-create" className="stack" onSubmit={criarSetor}>
        <div className="feedback-live" role="alert" aria-atomic="true">{errors.sector && <p className="error">{errors.sector}</p>}</div>
        <label>Sigla<input name="sigla" maxLength={30} required /></label>
        <label>Nome<input name="nome" maxLength={150} required /></label><button className="primary">Adicionar</button>
      </MutationForm>
    </Modal>
    <Modal open={modal === "edit-sector" && active} title={`Editar setor ${setorEmEdicao?.sigla ?? ""}`}
      onClose={close} closeLabel="Fechar edição do setor" description="O preenchimento é mantido ao fechar esta janela." busy={pending(editKey)}>
      <MutationForm formKey={editKey} className="stack edicao-setor" onSubmit={editarSetor} key={setorEmEdicao?.id}>
        <div className="feedback-live" role="alert" aria-atomic="true">{errors.edit && <p className="error">{errors.edit}</p>}</div>
        <label>Sigla do setor<input name="sigla" maxLength={30} value={sectorDraft.sigla}
          onChange={event => setSectorDraft({ ...sectorDraft, sigla: event.target.value })} required /></label>
        <label>Nome do setor<input name="nome" maxLength={150} value={sectorDraft.nome}
          onChange={event => setSectorDraft({ ...sectorDraft, nome: event.target.value })} required /></label>
        <button className="primary">Salvar alterações</button>
      </MutationForm>
    </Modal>
  </div>;
}
