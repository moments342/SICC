import { FormEvent, useCallback, useEffect, useState } from "react";
import { request } from "../api";
import { Badge } from "../components/Presentation";
import { opcoesDominio } from "../domainLabels";
import type { AuthenticatedPageProps, Setor } from "../models";
export function Administration({ token, notify }: AuthenticatedPageProps) {
  type UsuarioAdmin = {
    id: number; nome: string; email: string; login: string; perfil: string;
    ativo: boolean; senhaTemporaria: boolean;
  };
  const [users, setUsers] = useState<UsuarioAdmin[]>([]);
  const [selectedUser, setSelectedUser] = useState<UsuarioAdmin | null>(null);
  const [setores, setSetores] = useState<Setor[]>([]);
  const [setorEmEdicao, setSetorEmEdicao] = useState<Setor | null>(null);
  const load = useCallback(() => Promise.all([
    request<typeof users>("/api/v1/admin/usuarios", {}, token),
    request<Setor[]>("/api/v1/admin/setores", {}, token)
  ]).then(([usuariosCarregados, setoresCarregados]) => {
    setUsers(usuariosCarregados); setSetores(setoresCarregados);
  }), [token]);
  useEffect(() => { void load(); }, [load]);
  async function user(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    try { await request("/api/v1/admin/usuarios", { method: "POST", body: JSON.stringify({
      nome: f.get("nome"), email: f.get("email"), login: f.get("login"),
      senhaTemporaria: f.get("senha"), perfil: f.get("perfil")
    }) }, token); notify("Usuário criado com senha temporária."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  function extrairIdentidadeSetor(formulario: HTMLFormElement) {
    const dadosFormulario = new FormData(formulario);
    return { sigla: dadosFormulario.get("sigla"), nome: dadosFormulario.get("nome") };
  }
  async function criarSetor(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const formulario = e.currentTarget;
    try { await request("/api/v1/admin/setores", {
      method: "POST", body: JSON.stringify(extrairIdentidadeSetor(formulario))
    }, token); formulario.reset(); notify("Setor incluído no catálogo."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function editarSetor(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (!setorEmEdicao) return;
    try { await request(`/api/v1/admin/setores/${setorEmEdicao.id}`, {
      method: "PUT", body: JSON.stringify(extrairIdentidadeSetor(e.currentTarget))
    }, token); setSetorEmEdicao(null); notify("Identidade do setor atualizada."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function resetPassword(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    try { await request(`/api/v1/admin/usuarios/${f.get("usuario")}/senha`, {
      method: "PATCH", body: JSON.stringify({ novaSenhaTemporaria: f.get("senha") })
    }, token); notify("Senha temporária redefinida; a troca será exigida no próximo acesso."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function toggleUser(id: number, ativo: boolean) {
    try { await request(`/api/v1/admin/usuarios/${id}/ativo?ativo=${!ativo}`, { method: "PATCH" }, token);
      notify(`Usuário ${ativo ? "desativado" : "reativado"}.`); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function changeProfile(id: number, perfil: string) {
    try { await request(`/api/v1/admin/usuarios/${id}/perfil?perfil=${perfil}`, { method: "PATCH" }, token);
      notify("Perfil de acesso atualizado."); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  async function detailUser(id: number) {
    try {
      setSelectedUser(await request<UsuarioAdmin>(`/api/v1/admin/usuarios/${id}`, {}, token));
    } catch (error) { notify((error as Error).message); }
  }
  async function toggleSector(id: number, ativo: boolean) {
    try { await request(`/api/v1/admin/setores/${id}/ativo?ativo=${!ativo}`, { method: "PATCH" }, token);
      notify(`Setor ${ativo ? "desativado" : "reativado"}.`); await load(); }
    catch (error) { notify((error as Error).message); }
  }
  return <div className="grid two"><section className="panel"><h2>Novo usuário DIPAC</h2><form className="stack" onSubmit={user}>
    <label>Nome<input name="nome" required /></label><label>E-mail<input name="email" type="email" required /></label>
    <label>Login imutável<input name="login" required /></label><label>Senha temporária<input name="senha" type="password" required /></label>
    <label>Perfil<select name="perfil">{opcoesDominio("perfilAcesso").map(opcao =>
      <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
    <button className="primary">Criar usuário</button></form><hr /><h2>Redefinir senha temporária</h2>
    <form className="inline-form" onSubmit={resetPassword}><label>Usuário Interno<select name="usuario" required>
      <option value="">Selecione</option>{users.map(usuario => <option key={usuario.id} value={usuario.id}>
        {usuario.nome} · @{usuario.login}
      </option>)}</select></label>
      <label>Nova senha temporária<input name="senha" type="password" required /></label>
      <button className="primary">Redefinir</button></form>{users.map(u => <article className="doc user-row" key={u.id}>
      <div><strong>{u.nome}</strong><small>@{u.login}</small></div><Badge value={u.ativo ? "ATIVO" : "INATIVO"} />
      <label>Perfil<select value={u.perfil} onChange={e => changeProfile(u.id, e.target.value)}>
        {opcoesDominio("perfilAcesso").map(opcao =>
          <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <button className="secondary-action" aria-label={`Ver detalhes de ${u.nome}`} onClick={() => detailUser(u.id)}>Detalhes</button>
      <button className={u.ativo ? "danger-action" : "secondary-action"}
        onClick={() => toggleUser(u.id, u.ativo)}>{u.ativo ? "Desativar" : "Reativar"}</button></article>)}
      {selectedUser && <article className="user-detail">
        <div><h3>Detalhes do Usuário Interno</h3><button aria-label="Fechar detalhes" onClick={() => setSelectedUser(null)}>×</button></div>
        <strong>{selectedUser.nome}</strong><span>{selectedUser.email}</span><span>@{selectedUser.login}</span>
        <Badge value={selectedUser.perfil} /><Badge value={selectedUser.ativo ? "ATIVO" : "INATIVO"} />
        {selectedUser.senhaTemporaria && <Badge value="TROCA_DE_SENHA_OBRIGATÓRIA" />}
      </article>}</section>
    <section className="panel catalogo-setores"><h2>Catálogo de setores</h2>
      <p className="muted">Somente setores ativos ficam disponíveis como novo destino de tramitação.</p>
      <form className="inline-form" onSubmit={criarSetor}>
        <label>Sigla<input name="sigla" maxLength={30} required /></label>
        <label>Nome<input name="nome" maxLength={150} required /></label>
        <button className="primary">Adicionar</button>
      </form>
      {setorEmEdicao && <form className="edicao-setor stack" onSubmit={editarSetor} key={setorEmEdicao.id}>
        <div className="panel-title"><h3>Editar setor {setorEmEdicao.sigla}</h3>
          <button type="button" onClick={() => setSetorEmEdicao(null)}>Cancelar edição</button></div>
        <div className="inline-form">
          <label>Sigla do setor<input name="sigla" maxLength={30} defaultValue={setorEmEdicao.sigla} required /></label>
          <label>Nome do setor<input name="nome" maxLength={150} defaultValue={setorEmEdicao.nome} required /></label>
          <button className="primary">Salvar alterações</button>
        </div>
      </form>}
      <div className="lista-setores">{setores.map(s =>
        <article className={`doc linha-setor ${s.ativo ? "ativo" : "inativo"}`} key={s.id}>
          <div><strong>{s.sigla}</strong><small>{s.nome}</small></div>
          <Badge value={s.ativo ? "ATIVO" : "INATIVO"} />
          <div className="acoes-setor">
            <button aria-label={`Editar setor ${s.sigla}`} onClick={() => setSetorEmEdicao(s)}>Editar</button>
            <button onClick={() => toggleSector(s.id, s.ativo)}>{s.ativo ? "Desativar" : "Reativar"}</button>
          </div>
        </article>)}
        {!setores.length && <p className="empty">Nenhum setor cadastrado.</p>}
      </div>
    </section></div>;
}
