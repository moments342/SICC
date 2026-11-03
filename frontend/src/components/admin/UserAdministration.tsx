import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { request } from "../../api";
import { opcoesDominio, rotuloDominio } from "../../domainLabels";
import { usePendingAction } from "../../usePendingAction";
import { useResource } from "../../useResource";
import type { AuthenticatedPageProps } from "../../models";
import { useConfirmation } from "../Confirmation";
import { ResourceState } from "../Feedback";
import { Modal } from "../Modal";
import { MutationForm } from "../MutationForm";
import { NewPasswordField } from "../NewPasswordField";
import { Badge } from "../Presentation";
import { useSessionActions, useSessionDraft } from "../SessionDrafts";

type User = {
  id: number; nome: string; email: string; login: string; perfil: string;
  ativo: boolean; senhaTemporaria: boolean;
};

const normalize = (value: string) => value.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLocaleLowerCase("pt-BR");

export function UserAdministration({ token, notify, active }: AuthenticatedPageProps & { active: boolean }) {
  const usersResource = useResource<User[]>("/api/v1/admin/usuarios", token);
  const users = usersResource.data ?? [];
  const [query, setQuery] = useState("");
  const [access, setAccess] = useState("TODOS");
  const searchInput = useRef<HTMLInputElement>(null);
  const directory = useRef<HTMLElement>(null);
  const restoreUser = useRef<{ id: number; previous?: User[] } | null>(null);
  const [modal, setModal] = useState<"create" | "manage" | "password" | null>(null);
  const [target, setTarget] = useState<User | null>(null);
  const detail = useResource<User>(active && modal === "manage" && target ? `/api/v1/admin/usuarios/${target.id}` : null, token);
  const selected = detail.data?.id === target?.id ? detail.data : undefined;
  const [profile, setProfile, clearProfile] = useSessionDraft(`admin:profile:${target?.id ?? 0}`, selected?.perfil ?? target?.perfil ?? "OPERADOR_DIPAC");
  const profileKey = `admin-user-profile:${target?.id ?? 0}`;
  const [errors, setErrors] = useState({ create: "", manage: "", password: "" });
  const [passwordUser, setPasswordUser] = useState<User | null>(null);
  const passwordRoot = useRef<HTMLDivElement>(null);
  const forms = useSessionActions();
  const actions = usePendingAction();
  const confirm = useConfirmation();
  const pending = (key: string) => forms.pending.has(`form:${key}`);
  const manageBusy = pending(profileKey) || actions.pending.has(`user:${target?.id}`);
  const visibleUsers = useMemo(() => {
    const search = normalize(query.trim());
    return users.filter(user => (access === "TODOS" || user.ativo === (access === "ATIVOS"))
      && (!search || normalize(`${user.nome} ${user.login} ${user.email}`).includes(search)));
  }, [usersResource.data, query, access]);
  const filtered = Boolean(query.trim()) || access !== "TODOS";
  const ready = Boolean(usersResource.data) && !usersResource.loading && !usersResource.error;
  useEffect(() => {
    if (modal || !ready || restoreUser.current === null || usersResource.data === restoreUser.current.previous) return;
    const button = directory.current?.querySelector<HTMLButtonElement>(`button[data-user-id="${restoreUser.current.id}"]`);
    (button ?? searchInput.current)?.focus({ preventScroll: true }); restoreUser.current = null;
  }, [modal, ready, usersResource.data]);

  function manage(user: User) {
    setTarget(user); setErrors(current => ({ ...current, manage: "" })); setModal("manage");
  }
  function openPassword(user: User) {
    if (passwordUser?.id !== user.id) passwordRoot.current?.querySelector("form")?.reset();
    setPasswordUser(user); setErrors(current => ({ ...current, password: "" })); setModal("password");
  }
  function clearFilters() { setQuery(""); setAccess("TODOS"); searchInput.current?.focus(); }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget, data = new FormData(form);
    setErrors(current => ({ ...current, create: "" }));
    try {
      await request("/api/v1/admin/usuarios", { method: "POST", body: JSON.stringify({
        nome: data.get("nome"), email: data.get("email"), login: data.get("login"),
        senhaTemporaria: data.get("senha"), perfil: data.get("perfil")
      }) }, token);
      form.reset(); setQuery(""); setAccess("TODOS"); setModal(null); usersResource.reload(); notify("Usuário criado com senha temporária.");
    } catch (error) { setErrors(current => ({ ...current, create: (error as Error).message })); return false; }
  }
  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selected) return false;
    setErrors(current => ({ ...current, manage: "" }));
    if (!await confirm({ title: "Alterar perfil de acesso?",
      message: `${selected.nome} passará a ter o perfil ${rotuloDominio(profile)}. As permissões de acesso seguirão esse perfil.`, confirmLabel: "Alterar perfil" })) {
      clearProfile(); return false;
    }
    try {
      await request(`/api/v1/admin/usuarios/${selected.id}/perfil?perfil=${profile}`, { method: "PATCH" }, token);
      restoreUser.current = { id: selected.id, previous: usersResource.data }; clearProfile(); setModal(null); usersResource.reload(); notify("Perfil de acesso atualizado.");
    } catch (error) { setErrors(current => ({ ...current, manage: (error as Error).message })); return false; }
  }
  async function toggleAccess(user: User) {
    setErrors(current => ({ ...current, manage: "" }));
    if (user.ativo && !await confirm({ title: "Desativar acesso?",
      message: `${user.nome} deixará de acessar o SICC. O histórico de suas operações será preservado.`,
      confirmLabel: "Desativar acesso", destructive: true })) return;
    try {
      await request(`/api/v1/admin/usuarios/${user.id}/ativo?ativo=${!user.ativo}`, { method: "PATCH" }, token);
      restoreUser.current = { id: user.id, previous: usersResource.data }; setModal(null); usersResource.reload(); notify(`Usuário ${user.ativo ? "desativado" : "reativado"}.`);
    } catch (error) { setErrors(current => ({ ...current, manage: (error as Error).message })); }
  }
  async function resetPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!passwordUser) return false;
    const form = event.currentTarget, data = new FormData(form);
    setErrors(current => ({ ...current, password: "" }));
    try {
      await request(`/api/v1/admin/usuarios/${passwordUser.id}/senha`, { method: "PATCH", body: JSON.stringify({ novaSenhaTemporaria: data.get("senha") }) }, token);
      restoreUser.current = { id: passwordUser.id, previous: usersResource.data }; form.reset(); setModal(null); usersResource.reload(); notify("Senha temporária redefinida; a troca será exigida no próximo acesso.");
    } catch (error) { setErrors(current => ({ ...current, password: (error as Error).message })); return false; }
  }

  return <>
    <section ref={directory} className="panel admin-users">
      <div className="user-directory-heading"><div><h2>Usuários DIPAC</h2><p className="muted">Gerencie perfis, senhas e acesso ao SICC.</p></div>
        <button className="primary admin-create" aria-haspopup="dialog" onClick={() => setModal("create")}>
          <svg viewBox="0 0 20 20" aria-hidden="true"><path d="M10 4v12M4 10h12" /></svg>Criar usuário</button>
      </div>
      <div className="user-directory-filters">
        <label>Buscar usuário<input ref={searchInput} type="search" value={query} placeholder="Nome, login ou e-mail" onChange={event => setQuery(event.target.value)} /></label>
        <label><span id="user-access-filter-label">Situação do acesso</span><select aria-labelledby="user-access-filter-label" value={access} onChange={event => setAccess(event.target.value)}>
          <option value="TODOS">Todos</option><option value="ATIVOS">Ativos</option><option value="INATIVOS">Inativos</option></select></label>
      </div>
      <div className="user-directory-summary">
        <p role="status">{ready && (filtered ? `${visibleUsers.length} de ${users.length} usuários` : `${users.length} ${users.length === 1 ? "usuário cadastrado" : "usuários cadastrados"}`)}</p>
        <div>{filtered && <button className="secondary-action" onClick={clearFilters}>Limpar filtros</button>}
          <button className="secondary-action" onClick={usersResource.reload} disabled={usersResource.loading}>Atualizar usuários</button></div>
      </div>
      <ResourceState {...usersResource} onRetry={usersResource.reload} label="os usuários" />
      {ready && visibleUsers.length > 0 && <table className="users-table" aria-label="Usuários DIPAC">
        <thead><tr><th scope="col">Usuário</th><th scope="col">Perfil</th><th scope="col">Acesso</th><th scope="col">Ações</th></tr></thead>
        <tbody>{visibleUsers.map(user => <tr className="user-row" key={user.id}>
          <td className="user-identity"><strong>{user.nome}</strong><small>@{user.login}</small></td>
          <td data-label="Perfil">{rotuloDominio(user.perfil)}</td>
          <td data-label="Acesso"><Badge value={user.ativo ? "ATIVO" : "INATIVO"} />{user.senhaTemporaria && <small>Troca de senha pendente</small>}</td>
          <td className="user-row-action"><button className="secondary-action" data-user-id={user.id} aria-haspopup="dialog" aria-expanded={(modal === "manage" || modal === "password") && target?.id === user.id} aria-label={`Gerenciar usuário ${user.nome}`} onClick={() => manage(user)}>Gerenciar</button></td>
        </tr>)}</tbody>
      </table>}
      {ready && !visibleUsers.length && <p className="empty">{users.length ? "Nenhum usuário corresponde aos filtros." : "Nenhum usuário cadastrado."}</p>}
    </section>

    <Modal open={modal === "create" && active} title="Criar usuário" closeLabel="Fechar criação de usuário" onClose={() => setModal(null)} busy={pending("admin-user-create")}
      description="O login será imutável. A senha temporária deverá ser trocada no primeiro acesso.">
      <MutationForm formKey="admin-user-create" className="stack" onSubmit={create}>
        <div className="feedback-live" role="alert" aria-atomic="true">{errors.create && <p className="error">{errors.create}</p>}</div>
        <label>Nome<input name="nome" autoComplete="off" required /></label><label>E-mail<input name="email" type="email" autoComplete="off" required /></label>
        <label>Login imutável<input name="login" autoComplete="off" required /></label><NewPasswordField label="Senha temporária" name="senha" />
        <label>Perfil<select name="perfil">{opcoesDominio("perfilAcesso").map(option => <option key={option.codigo} value={option.codigo}>{option.rotulo}</option>)}</select></label>
        <button className="primary">Criar usuário</button>
      </MutationForm>
    </Modal>

    <Modal open={modal === "manage" && active} title="Gerenciar usuário" closeLabel="Fechar gerenciamento do usuário" onClose={() => setModal(null)} busy={manageBusy}
      description={target ? `${target.nome} · @${target.login}` : undefined}>
      <ResourceState {...detail} onRetry={detail.reload} label="os dados do usuário" />
      {selected && <div className="user-management-content">
        <dl className="user-management-identity"><div><dt>E-mail</dt><dd>{selected.email}</dd></div><div><dt>Acesso atual</dt><dd><Badge value={selected.ativo ? "ATIVO" : "INATIVO"} /></dd></div></dl>
        {selected.senhaTemporaria && <p className="muted">Troca de senha obrigatória no próximo acesso.</p>}
        <MutationForm formKey={profileKey} className="user-profile-form" onSubmit={saveProfile}>
          <div className="feedback-live" role="alert" aria-atomic="true">{errors.manage && <p className="error">{errors.manage}</p>}</div>
          <label><span id="user-profile-label">Perfil</span><select aria-labelledby="user-profile-label" value={profile} disabled={manageBusy} onChange={event => setProfile(event.target.value)}>{opcoesDominio("perfilAcesso").map(option => <option key={option.codigo} value={option.codigo}>{option.rotulo}</option>)}</select></label>
          <p className="muted form-hint">O Administrador DIPAC gerencia usuários e setores. O Operador DIPAC usa os módulos operacionais.</p>
          <button className="primary" disabled={manageBusy || profile === selected.perfil}>Salvar perfil</button>
        </MutationForm>
        <section className="user-access-section"><h3>Senha e acesso</h3><p className="muted">A redefinição cria uma senha temporária. A desativação bloqueia o acesso e preserva o histórico.</p>
          <div className="user-access-actions"><button className="secondary-action" disabled={manageBusy} aria-haspopup="dialog" aria-label={`Redefinir senha de ${selected.nome}`} onClick={() => openPassword(selected)}>Redefinir senha</button>
            <button className={selected.ativo ? "danger-action" : "secondary-action"} disabled={manageBusy} onClick={() => void actions.run(`user:${selected.id}`, () => toggleAccess(selected))}>{selected.ativo ? "Desativar acesso" : "Reativar acesso"}</button></div>
        </section>
      </div>}
    </Modal>

    <Modal open={modal === "password" && active} title="Redefinir senha temporária" closeLabel="Fechar redefinição de senha" onClose={() => setModal("manage")} busy={pending("admin-password")}
      description={passwordUser ? `${passwordUser.nome} · @${passwordUser.login}. A troca será exigida no próximo acesso.` : undefined}>
      <div ref={passwordRoot}><MutationForm formKey="admin-password" className="stack" onSubmit={resetPassword}>
        <div className="feedback-live" role="alert" aria-atomic="true">{errors.password && <p className="error">{errors.password}</p>}</div>
        <NewPasswordField label="Nova senha temporária" name="senha" />
        <div className="user-access-actions"><button type="button" className="secondary-action" disabled={pending("admin-password")} onClick={() => setModal("manage")}>Voltar ao usuário</button><button className="primary" disabled={!passwordUser}>Redefinir</button></div>
      </MutationForm></div>
    </Modal>
  </>;
}
