import { MutationForm } from "../MutationForm";
import { Modal } from "../Modal";
import { useConfirmation } from "../Confirmation";
import { useSessionActions } from "../SessionDrafts";
import { ResourceState } from "../Feedback";
import { useResource } from "../../useResource";
import type { Notify } from "../../models";
import { FormEvent, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { download, request } from "../../api";
import { opcoesDominio, rotuloDominio } from "../../domainLabels";
import { responsavelSelecionado } from "../../formValues";
import { dataLocalAtual, formatarDataNegocio, money } from "../../formatters";
import type { Documento, Instrumento, ProcessoAdministrativo, ResponsavelProcesso } from "../../models";

type DetailsProps = {
  token: string; notify: Notify; processo: ProcessoAdministrativo;
  onUpdated: (processo: ProcessoAdministrativo | null) => void; onChanged: () => void;
  active: boolean;
  actionContainer?: HTMLElement | null;
};

export function InactiveProcessSummary({ processo }: { processo: ProcessoAdministrativo }) {
  return <section className="panel span"><h2>Registro histórico</h2>
    <p className="muted">Registro desativado preservado para consulta histórica.</p>
    <p><strong>Origem:</strong> {processo.origem}</p><p><strong>Status:</strong> {rotuloDominio(processo.status)}</p>
    <p><strong>Setor atual:</strong> {processo.setorAtual ?? "sem movimentação"}</p></section>;
}

export function ProcessAdministrationPanel({ token, notify, processo, responsaveis, onUpdated, onChanged, active, actionContainer }:
  DetailsProps & { responsaveis: ResponsavelProcesso[] }) {
  const [origem, setOrigem] = useState(processo.origem);
  const [projeto, setProjeto] = useState(processo.numeroProjeto ?? "");
  const [responsavelId, setResponsavelId] = useState(String(processo.responsavel?.id ?? ""));
  const deactivating = useRef(false);
  const [pendingDeactivation, setPendingDeactivation] = useState(false);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState("");
  const confirm = useConfirmation();
  const formKey = `process-edit:${processo.id}`;
  const busy = useSessionActions().pending.has(`form:${formKey}`);

  async function edit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    setError("");
    try {
      const updated = await request<ProcessoAdministrativo>(`/api/v1/processos/${processo.id}`, {
        method: "PUT", body: JSON.stringify({
          origem: form.get("origem"), numeroProjeto: form.get("projeto") || null, responsavelId: responsavelSelecionado(form)
        })
      }, token);
      onUpdated(updated); setOpen(false); notify("Processo Administrativo atualizado."); onChanged();
    } catch (error) { setError((error as Error).message); return false; }
  }
  async function deactivate() {
    if (deactivating.current) return;
    deactivating.current = true;
    try {
      if (!await confirm({ title: "Desativar Processo Administrativo?", message: `O processo ${processo.numero} sairá do catálogo de ativos. O registro histórico será preservado. Esta tela não oferece reativação.`,
        confirmLabel: "Desativar processo", destructive: true })) return;
      setPendingDeactivation(true);
      await request(`/api/v1/processos/${processo.id}`, { method: "DELETE" }, token);
      onUpdated(null); notify("Processo Administrativo desativado; o registro histórico foi preservado."); onChanged();
    } catch (error) { notify((error as Error).message, "error"); return false; }
    finally { deactivating.current = false; setPendingDeactivation(false); }
  }

  const action = <button className="secondary-action" aria-haspopup="dialog" onClick={() => setOpen(true)} disabled={pendingDeactivation}>Editar processo</button>;
  return <section className="panel span process-administration"><div className="panel-title"><h2>Dados administrativos</h2>
    {actionContainer ? createPortal(action, actionContainer) : action}</div>
    <dl className="instrument-data"><dt>Origem</dt><dd>{processo.origem}</dd>
      <dt>Número do projeto</dt><dd>{processo.numeroProjeto || "Não informado"}</dd>
      <dt>Responsável DIPAC</dt><dd>{processo.responsavel?.nome ?? "Sem responsável"}</dd></dl>
    <button type="button" className="danger-action" disabled={pendingDeactivation || busy} onClick={deactivate}>
      {pendingDeactivation ? "Desativando…" : "Desativar processo"}</button>
    <Modal open={open && active} title="Editar Processo Administrativo" description={`Processo ${processo.numero}. O preenchimento é mantido ao fechar esta janela.`}
      busy={busy} closeLabel="Fechar edição do processo" onClose={() => setOpen(false)}>
    <MutationForm formKey={formKey} className="stack" onSubmit={edit}>
      <div className="feedback-live" role="alert" aria-atomic="true">{error && <p className="error">{error}</p>}</div>
      <label>Origem<input name="origem" maxLength={150} value={origem}
      onChange={event => setOrigem(event.target.value)} required /></label>
      <label>Número do projeto<input name="projeto" maxLength={80} value={projeto} onChange={event => setProjeto(event.target.value)} /></label>
      <label>Responsável DIPAC<select name="responsavel" value={responsavelId}
        onChange={event => setResponsavelId(event.target.value)}><option value="">Sem responsável</option>
        {responsaveis.map(responsavel => <option key={responsavel.id} value={responsavel.id}>{responsavel.nome}</option>)}
      </select></label><button className="primary" disabled={pendingDeactivation}>Salvar</button>
    </MutationForm></Modal></section>;
}

export function InstrumentFormalizationPanel({ token, notify, processo, onUpdated, onChanged, active, onDocuments, actionContainer }: DetailsProps & { active: boolean; onDocuments: () => void }) {
  const confirm = useConfirmation();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState("");
  const [documentId, setDocumentId] = useState("");
  const formKey = `process-formalize:${processo.id}`;
  const busy = useSessionActions().pending.has(`form:${formKey}`);
  const documents = useResource<Documento[]>(active && open ? `/api/v1/documentos?proprietarioTipo=PROCESSO&proprietarioId=${processo.id}` : null, token);
  const documentos = (documents.data ?? []).filter(item => item.ativo && item.categoria === "ASSINADO" && item.versoes[0]?.tipoMime === "application/pdf");

  async function formalize(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget);
    setError("");
    const documento = documentos.find(item => item.id === Number(data.get("documento")));
    if (!documento || !data.get("tipo")) return false;
    const summary = [
      ["Processo Administrativo", processo.numero], ["Número do instrumento", String(data.get("numero"))],
      ["Tipo do instrumento", rotuloDominio(String(data.get("tipo")))],
      ["Objeto", String(data.get("objeto"))], ["Natureza", String(data.get("natureza"))],
      ["Coordenador", String(data.get("coordenador"))], ["Partícipes", String(data.get("participes"))],
      ["Valor", money(Number(data.get("valor")))],
      ["Vigência contratual", formatarDataNegocio(String(data.get("contratual")))],
      ["Vigência do TED", data.get("ted") ? formatarDataNegocio(String(data.get("ted"))) : "Não informada"],
      ["Data de formalização", formatarDataNegocio(String(data.get("data")))],
      ["Documento Assinado PDF", `${documento.titulo} · versão ${documento.versoes[0]?.versao}`]
    ];
    if (!await confirm({ title: "Conferir formalização", confirmLabel: "Confirmar formalização",
      message: "Confira os dados antes de formalizar. O número, o tipo e o Processo Administrativo vinculado definem a identidade do instrumento e não poderão ser editados após a formalização.",
      details: <dl className="confirmation-summary">{summary.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
    })) return false;
    try {
      const instrumento = await request<Instrumento>(`/api/v1/processos/${processo.id}/instrumento`, {
        method: "POST", body: JSON.stringify({
          numero: data.get("numero"), tipo: data.get("tipo"), objeto: data.get("objeto"),
          descricao: data.get("descricao"), natureza: data.get("natureza"), coordenador: data.get("coordenador"),
          participes: String(data.get("participes")).split("\n").map(item => item.trim()).filter(Boolean),
          valorAtual: Number(data.get("valor")), vigenciaContratualFinal: data.get("contratual"),
          vigenciaTedFinal: data.get("ted") || null, dataFormalizacao: data.get("data"),
          documentoAssinadoId: Number(data.get("documento"))
        })
      }, token);
      setOpen(false); onUpdated({ ...processo, instrumento }); notify("Instrumento Contratual formalizado."); onChanged();
      // The POST already confirmed the operation. A refresh failure must never invite a repeat POST.
      try { onUpdated(await request<ProcessoAdministrativo>(`/api/v1/processos/${processo.id}`, {}, token)); }
      catch { notify("Instrumento Contratual formalizado. Não foi possível atualizar os demais dados do processo; volte à lista e consulte novamente.", "error"); }
    } catch (error) { setError((error as Error).message); return false; }
  }

  const action = <button className="primary" aria-haspopup="dialog" onClick={() => setOpen(true)}>Formalizar Instrumento Contratual</button>;
  return <section className="panel span process-formalization"><div className="panel-title"><h2>Instrumento Contratual</h2>
    {actionContainer ? createPortal(action, actionContainer) : action}</div>
    <p className="muted">Ainda não formalizado. Cadastre os dados contratuais e vincule um Documento Assinado PDF.</p>
    <Modal open={open && active} wide title="Formalizar Instrumento Contratual" busy={busy} onClose={() => setOpen(false)}
      closeLabel="Fechar formalização" description={`Processo ${processo.numero}. O preenchimento é mantido ao fechar a janela ou consultar Documentos.`}>
    <button className="secondary-action" disabled={busy} onClick={() => { setOpen(false); onDocuments(); }}>Anexar documento deste processo</button>
    <p className="muted">O Documento Assinado deve estar em PDF e vinculado a este Processo Administrativo.</p>
    <p className="muted form-hint">Descrição e vigência do TED são opcionais. Os demais dados são necessários para formalizar.</p>
    <ResourceState {...documents} onRetry={documents.reload} label="os documentos assinados" />
    <MutationForm formKey={formKey} className="process-formalization-form" onSubmit={formalize}>
      <div className="feedback-live field-full" role="alert" aria-atomic="true">{error && <p className="error">{error}</p>}</div>
      <fieldset className="formalization-group field-full"><legend>Identidade e participantes</legend><div className="formalization-group-fields">
      <label>Número<input name="numero" maxLength={60} required /></label>
      <label>Tipo<select name="tipo" required defaultValue=""><option value="">Selecione o tipo</option>{opcoesDominio("tipoInstrumento").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label className="field-full">Objeto<textarea name="objeto" rows={2} maxLength={1000} required /></label><label className="field-full">Descrição<textarea name="descricao" rows={2} maxLength={2000} /></label>
      <label>Natureza<input name="natureza" maxLength={150} required /></label><label>Coordenador<input name="coordenador" maxLength={150} required /></label>
      <label className="field-full">Partícipes, um por linha<textarea name="participes" rows={3} required /></label>
      </div></fieldset>
      <fieldset className="formalization-group field-full"><legend>Condições e vigências</legend><div className="formalization-group-fields">
      <label>Valor<input name="valor" type="number" min="0" step=".01" required /></label>
      <label>Vigência contratual<input name="contratual" type="date" required /></label>
      <label>Vigência TED<input name="ted" type="date" /></label>
      </div></fieldset>
      <fieldset className="formalization-group field-full"><legend>Comprovação da formalização</legend><div className="formalization-group-fields">
      <label>Data de formalização<input name="data" type="date" max={dataLocalAtual()} required /></label>
      <label className="field-full">Documento assinado PDF<select name="documento" required value={documentId} onChange={event => setDocumentId(event.target.value)}><option value="">Selecione</option>
        {documentos.map(documento => <option key={documento.id} value={documento.id}>
          #{documento.id} · {documento.titulo} · versão {documento.versoes[0]?.versao}</option>)}</select></label>
      </div></fieldset>
      <button className="primary" disabled={documents.loading || !!documents.error || !documentos.length || !documentos.some(item => String(item.id) === documentId)}>Formalizar</button></MutationForm></Modal></section>;
}

export function InstrumentSummary({ instrumento, token, notify, onDocuments }: { instrumento: Instrumento; token: string; notify: Notify; onDocuments?: () => void }) {
  return <section className="panel span instrumento-resumo"><div className="panel-title"><h2>Instrumento Contratual · {instrumento.numero}</h2>
    {onDocuments && <button className="secondary-action" onClick={onDocuments}>Documentos do instrumento</button>}</div>
    <div className="tramitacao-metrics"><div><small>Tipo</small><strong>{rotuloDominio(instrumento.tipo)}</strong></div>
      <div><small>Coordenador</small><strong>{instrumento.coordenador}</strong></div>
      <div><small>Vigência contratual final</small><strong>{formatarDataNegocio(instrumento.vigenciaContratualFinal)}</strong></div>
      <div><small>Vigência TED final</small><strong>{instrumento.vigenciaTedFinal ? formatarDataNegocio(instrumento.vigenciaTedFinal) : "Não informada"}</strong></div>
    </div><p className="instrument-value"><strong>Valor atual</strong> {money(instrumento.valorAtual)}</p>
    <details className="instrument-more"><summary>Objeto e demais dados do instrumento</summary><dl className="instrument-data">
      <dt>Objeto</dt><dd>{instrumento.objeto || "Não informado"}</dd>
      <dt>Descrição</dt><dd>{instrumento.descricao || "Não informada"}</dd>
      <dt>Natureza</dt><dd>{instrumento.natureza || "Não informada"}</dd>
      <dt>Partícipes</dt><dd>{instrumento.participes?.join("\n") || "Não informados"}</dd>
    </dl></details><button className="secondary-action" onClick={() => void download(`/api/v1/documentos/${instrumento.documentoAssinadoId}/versoes/${instrumento.documentoAssinadoVersao}/arquivo`, token).catch(error => notify(error.message, "error"))}>Baixar documento assinado</button>
    <p className="muted">Documento assinado #{instrumento.documentoAssinadoId}, versão {instrumento.documentoAssinadoVersao}
      {" · SHA-256 "}{instrumento.documentoAssinadoChecksumSha256.slice(0, 12)}…</p></section>;
}
