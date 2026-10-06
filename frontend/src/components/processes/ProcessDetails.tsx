import type { Notify } from "../../models";
import { FormEvent, useEffect, useRef, useState } from "react";
import { request } from "../../api";
import { opcoesDominio, rotuloDominio } from "../../domainLabels";
import { responsavelSelecionado } from "../../formValues";
import { dataLocalAtual } from "../../formatters";
import type { Documento, Instrumento, ProcessoAdministrativo, ResponsavelProcesso } from "../../models";

type DetailsProps = {
  token: string; notify: Notify; processo: ProcessoAdministrativo;
  onUpdated: (processo: ProcessoAdministrativo | null) => void; onChanged: () => void;
};

export function InactiveProcessSummary({ processo }: { processo: ProcessoAdministrativo }) {
  return <section className="panel span"><h2>Processo Administrativo · {processo.numero}</h2>
    <p className="muted">Registro desativado preservado para consulta histórica.</p>
    <p><strong>Origem:</strong> {processo.origem}</p><p><strong>Status:</strong> {rotuloDominio(processo.status)}</p>
    <p><strong>Setor atual:</strong> {processo.setorAtual ?? "sem movimentação"}</p></section>;
}

export function ProcessAdministrationPanel({ token, notify, processo, responsaveis, onUpdated, onChanged }:
  DetailsProps & { responsaveis: ResponsavelProcesso[] }) {
  const [origem, setOrigem] = useState(processo.origem);
  const [projeto, setProjeto] = useState(processo.numeroProjeto ?? "");
  const [responsavelId, setResponsavelId] = useState(String(processo.responsavel?.id ?? ""));
  const deactivating = useRef(false);
  const [pendingDeactivation, setPendingDeactivation] = useState(false);

  async function edit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    try {
      const updated = await request<ProcessoAdministrativo>(`/api/v1/processos/${processo.id}`, {
        method: "PUT", body: JSON.stringify({
          origem: form.get("origem"), numeroProjeto: form.get("projeto") || null, responsavelId: responsavelSelecionado(form)
        })
      }, token);
      onUpdated(updated); notify("Processo Administrativo atualizado."); onChanged();
    } catch (error) { notify((error as Error).message, "error"); }
  }
  async function deactivate() {
    if (deactivating.current) return;
    if (!window.confirm(`Desativar o Processo Administrativo ${processo.numero}?\n\nEle sairá do catálogo de ativos. O registro histórico será preservado. Esta tela não oferece reativação.`)) return;
    deactivating.current = true;
    setPendingDeactivation(true);
    try {
      await request(`/api/v1/processos/${processo.id}`, { method: "DELETE" }, token);
      onUpdated(null); notify("Processo Administrativo desativado; o registro histórico foi preservado."); onChanged();
    } catch (error) { notify((error as Error).message, "error"); }
    finally { deactivating.current = false; setPendingDeactivation(false); }
  }

  return <section className="panel span"><h2>Editar Processo Administrativo · {processo.numero}</h2>
    <form className="inline-form" onSubmit={edit}><label>Origem<input name="origem" maxLength={150} value={origem}
      onChange={event => setOrigem(event.target.value)} required /></label>
      <label>Número do projeto<input name="projeto" maxLength={80} value={projeto} onChange={event => setProjeto(event.target.value)} /></label>
      <label>Responsável DIPAC<select name="responsavel" value={responsavelId}
        onChange={event => setResponsavelId(event.target.value)}><option value="">Sem responsável</option>
        {responsaveis.map(responsavel => <option key={responsavel.id} value={responsavel.id}>{responsavel.nome}</option>)}
      </select></label><button className="primary" disabled={pendingDeactivation}>Salvar</button>
      <button type="button" disabled={pendingDeactivation} onClick={deactivate}>
        {pendingDeactivation ? "Desativando…" : "Desativar"}</button>
    </form></section>;
}

export function InstrumentFormalizationPanel({ token, notify, processo, onUpdated, onChanged }: DetailsProps) {
  const [documentos, setDocumentos] = useState<Documento[]>([]);
  useEffect(() => {
    const controller = new AbortController(); setDocumentos([]);
    void request<Documento[]>(`/api/v1/documentos?proprietarioTipo=PROCESSO&proprietarioId=${processo.id}`,
      { signal: controller.signal }, token)
      .then(items => setDocumentos(items.filter(item => item.ativo && item.categoria === "ASSINADO"
        && item.versoes[0]?.tipoMime === "application/pdf")))
      .catch(error => { if ((error as Error).name !== "AbortError") notify((error as Error).message, "error"); });
    return () => controller.abort();
  }, [notify, processo.id, token]);

  async function formalize(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget);
    try {
      await request<Instrumento>(`/api/v1/processos/${processo.id}/instrumento`, {
        method: "POST", body: JSON.stringify({
          numero: data.get("numero"), tipo: data.get("tipo"), objeto: data.get("objeto"),
          descricao: data.get("descricao"), natureza: data.get("natureza"), coordenador: data.get("coordenador"),
          participes: String(data.get("participes")).split("\n").map(item => item.trim()).filter(Boolean),
          valorAtual: Number(data.get("valor")), vigenciaContratualFinal: data.get("contratual"),
          vigenciaTedFinal: data.get("ted") || null, dataFormalizacao: data.get("data"),
          documentoAssinadoId: Number(data.get("documento"))
        })
      }, token);
      const updated = await request<ProcessoAdministrativo>(`/api/v1/processos/${processo.id}`, {}, token);
      onUpdated(updated); notify("Instrumento Contratual formalizado."); onChanged();
    } catch (error) { notify((error as Error).message, "error"); }
  }

  return <section className="panel span"><h2>Formalizar Instrumento Contratual</h2>
    <p className="muted">Crie primeiro um Documento Assinado PDF vinculado ao Processo Administrativo.</p>
    <form className="inline-form" onSubmit={formalize}><label>Número<input name="numero" maxLength={60} required /></label>
      <label>Tipo<select name="tipo">{opcoesDominio("tipoInstrumento").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
      <label>Objeto<input name="objeto" maxLength={1000} required /></label><label>Descrição<input name="descricao" maxLength={2000} /></label>
      <label>Natureza<input name="natureza" maxLength={150} required /></label><label>Coordenador<input name="coordenador" maxLength={150} required /></label>
      <label>Partícipes, um por linha<textarea name="participes" rows={3} required /></label>
      <label>Valor<input name="valor" type="number" min="0" step=".01" required /></label>
      <label>Vigência contratual<input name="contratual" type="date" required /></label>
      <label>Vigência TED<input name="ted" type="date" /></label>
      <label>Data de formalização<input name="data" type="date" max={dataLocalAtual()} required /></label>
      <label>Documento assinado PDF<select name="documento" required><option value="">Selecione</option>
        {documentos.map(documento => <option key={documento.id} value={documento.id}>
          #{documento.id} · {documento.titulo} · versão {documento.versoes[0]?.versao}</option>)}</select></label>
      <button className="primary">Formalizar</button></form></section>;
}

export function InstrumentSummary({ instrumento }: { instrumento: Instrumento }) {
  return <section className="panel span instrumento-resumo"><h2>Instrumento Contratual · {instrumento.numero}</h2>
    <div className="tramitacao-metrics"><div><small>Tipo</small><strong>{rotuloDominio(instrumento.tipo)}</strong></div>
      <div><small>Coordenador</small><strong>{instrumento.coordenador}</strong></div>
      <div><small>Vigência contratual final</small><strong>{instrumento.vigenciaContratualFinal}</strong></div>
      <div><small>Vigência TED final</small><strong>{instrumento.vigenciaTedFinal ?? "Não informada"}</strong></div>
    </div><p className="muted">Documento assinado #{instrumento.documentoAssinadoId}, versão {instrumento.documentoAssinadoVersao}
      {" · SHA-256 "}{instrumento.documentoAssinadoChecksumSha256.slice(0, 12)}…</p></section>;
}
