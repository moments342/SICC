import { MutationForm } from "../components/MutationForm";
import { Modal } from "../components/Modal";
import { useConfirmation } from "../components/Confirmation";
import { useSessionActions } from "../components/SessionDrafts";
import { FormEvent, useEffect, useRef, useState } from "react";
import { download, request, upload } from "../api";
import { useResource } from "../useResource";
import { useRouteState } from "../useRouteState";
import { usePendingAction } from "../usePendingAction";
import { ResourceState } from "../components/Feedback";
import { categoriaDocumento, formatosPermitidosDocumento } from "../documentCatalog";
import { opcoesDominio, rotuloDominio } from "../domainLabels";
import type {
  ProprietarioDocumento, ProcessoAdministrativo, ProcessDocumentTarget, AuthenticatedPageProps,
  CategoriaDocumento, TipoProprietarioDocumento, Documento
} from "../models";
import { SeletorProprietarioDocumento } from "../components/documents/SeletorProprietarioDocumento";

export function Documents({ token, notify, processContext, contextTarget, onReturn, active }: AuthenticatedPageProps & {
  processContext: ProcessoAdministrativo | null; onReturn: () => void; active: boolean;
  contextTarget?: ProcessDocumentTarget;
}) {
  const createRoot = useRef<HTMLDivElement>(null);
  const [route, updateRoute] = useRouteState("documentos", ["vinculo", "tipo"]);
  const [linkError, setLinkError] = useState("");
  const [linkLoading, setLinkLoading] = useState(false);
  const [linkAttempt, setLinkAttempt] = useState(0);
  const pickerToggle = useRef<HTMLButtonElement>(null);
  const versionRoot = useRef<HTMLDivElement>(null);
  const dirty = useRef({ create: false, version: false });
  const actions = usePendingAction();
  const confirm = useConfirmation();
  const formActions = useSessionActions();
  const [modal, setModal] = useState<"create" | "version" | null>(null);
  const [ownerType, setOwnerType] = useState<TipoProprietarioDocumento>("PROCESSO");
  const [proprietario, setProprietario] = useState<ProprietarioDocumento | null>(null);
  const [pickerOpen, setPickerOpen] = useState(true);
  const [expandedHistories, setExpandedHistories] = useState<Record<number, boolean>>({});
  const ownerId = proprietario ? String(proprietario.id) : "";
  const [category, setCategory] = useState<CategoriaDocumento>("ADMINISTRATIVO");
  const [versionDocumentId, setVersionDocumentId] = useState("");
  const [versionSnapshot, setVersionSnapshot] = useState<Documento | null>(null);
  const [createError, setCreateError] = useState("");
  const [versionError, setVersionError] = useState("");
  const createPending = formActions.pending.has("form:document-create");
  const versionPending = formActions.pending.has("form:document-version");

  useEffect(() => {
    if (!active) return;
    const id = Number(route.vinculo);
    const validId = Number.isSafeInteger(id) && id > 0;
    const tipo = opcoesDominio("proprietarioDocumento").some(item => item.codigo === route.tipo) ? route.tipo as TipoProprietarioDocumento : "PROCESSO";
    if (proprietario?.id === id && ownerType === tipo) return;
    const controller = new AbortController(); setLinkError(""); setLinkLoading(false);
    void (async () => {
      if (!await canChangeOwner()) {
        if (!controller.signal.aborted) updateRoute({ vinculo: proprietario ? String(proprietario.id) : "", tipo: ownerType }, true);
        return;
      }
      if (controller.signal.aborted) return;
      resetDrafts(); setModal(null); setProprietario(null); setOwnerType(tipo); setPickerOpen(true);
      if (!validId) return;
      setLinkLoading(true);
      try {
        const value = await request<ProprietarioDocumento>(`/api/v1/documentos/proprietarios/${id}?tipo=${tipo}`, { signal: controller.signal }, token);
        if (controller.signal.aborted) return;
        if (value.id !== id) throw new Error("O vínculo solicitado não está disponível.");
        setOwnerType(tipo); setProprietario(value); setPickerOpen(false);
      } catch (error) { if (!controller.signal.aborted) setLinkError((error as Error).message); }
      finally { if (!controller.signal.aborted) setLinkLoading(false); }
    })();
    return () => controller.abort();
  }, [route.vinculo, route.tipo, active, token, linkAttempt]);

  function resetDrafts() {
    createRoot.current?.querySelector("form")?.reset();
    versionRoot.current?.querySelector("form")?.reset();
    dirty.current = { create: false, version: false };
    setCreateError(""); setVersionError(""); setVersionDocumentId(""); setVersionSnapshot(null);
  }
  async function canChangeOwner() {
    if (createPending || versionPending) {
      notify("Aguarde a confirmação do envio antes de trocar o vínculo.", "error"); return false;
    }
    return !(dirty.current.create || dirty.current.version)
      || await confirm({ title: "Descartar preenchimento?", message: "Há preenchimento de documento em andamento. Trocar o vínculo descartará o título e os arquivos selecionados que ainda não foram enviados.",
        confirmLabel: "Descartar e trocar vínculo", destructive: true });
  }
  useEffect(() => {
    if (!processContext) return;
    let cancelled = false;
    void (async () => {
      if (!await canChangeOwner() || cancelled) return;
      resetDrafts();
      const instrument = contextTarget?.tipo === "INSTRUMENTO" ? processContext.instrumento : null;
      setOwnerType(instrument ? "INSTRUMENTO" : "PROCESSO"); setCategory(contextTarget?.anexarAssinado ? "ASSINADO" : "ADMINISTRATIVO");
      setProprietario({ id: instrument?.id ?? processContext.id, numero: instrument?.numero ?? processContext.numero, numeroProcesso: processContext.numero,
        origem: processContext.origem, numeroInstrumento: processContext.instrumento?.numero ?? null,
        tipoInstrumento: processContext.instrumento?.tipo ?? null, estadoAlteracao: null,
        statusProcesso: processContext.status, processoAtivo: processContext.ativo });
      setModal(processContext.ativo && contextTarget?.anexarAssinado ? "create" : null);
      setPickerOpen(false);
      updateRoute({ vinculo: String(instrument?.id ?? processContext.id), tipo: instrument ? "INSTRUMENTO" : "PROCESSO" });
    })();
    return () => { cancelled = true; };
  }, [processContext]);

  const documents = useResource<Documento[]>(ownerId
    ? `/api/v1/documentos?proprietarioTipo=${ownerType}&proprietarioId=${ownerId}&incluirInativos=true`
    : null, token);
  const items = documents.data ?? [];
  const search = documents.reload;
  const formatosCriacao = formatosPermitidosDocumento(category);
  const documentoVersionado = items.find(item => String(item.id) === versionDocumentId) ?? versionSnapshot;
  const versionAvailable = !documents.loading && !documents.error
    && items.some(item => item.ativo && String(item.id) === versionDocumentId);
  const formatosNovaVersao = documentoVersionado ? formatosPermitidosDocumento(documentoVersionado.categoria) : null;
  const proprietarioSelecionadoAtivo = proprietario?.processoAtivo === true;
  const proprietarioSelecionadoInativo = proprietario?.processoAtivo === false;

  async function selecionarTipoProprietario(valor: string) {
    if (!await canChangeOwner()) return;
    resetDrafts(); setModal(null); setOwnerType(valor as TipoProprietarioDocumento); setProprietario(null); updateRoute({ tipo: valor, vinculo: "" });
  }
  async function selecionarProprietario(value: ProprietarioDocumento | null) {
    if (value?.id === proprietario?.id || !await canChangeOwner()) return;
    resetDrafts(); setModal(null); setProprietario(value);
    updateRoute({ tipo: ownerType, vinculo: value ? String(value.id) : "" });
    if (value) { setPickerOpen(false); requestAnimationFrame(() => pickerToggle.current?.focus({ preventScroll: true })); }
  }
  async function openVersion(documento: Documento) {
    if (versionDocumentId !== String(documento.id)) {
      if (dirty.current.version && !await confirm({ title: "Trocar documento da nova versão?", message: `O arquivo ainda não enviado será descartado para preparar uma versão de ${documento.titulo}. As versões já armazenadas não serão alteradas.`,
        confirmLabel: "Descartar arquivo e trocar", destructive: true })) return;
      versionRoot.current?.querySelector("form")?.reset();
      dirty.current.version = false; setVersionError(""); setVersionDocumentId(String(documento.id));
    }
    setVersionSnapshot(documento);
    setModal("version");
  }
  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget; const f = new FormData(form); const body = new FormData();
    setCreateError("");
    body.append("proprietarioTipo", ownerType); body.append("proprietarioId", ownerId);
    body.append("categoria", category); body.append("titulo", String(f.get("titulo")));
    body.append("arquivo", f.get("arquivo")!);
    try {
      await upload("/api/v1/documentos", body, token);
      form.reset(); dirty.current.create = false; setModal(null);
      notify("Documento e primeira versão armazenados."); search();
    } catch (error) { setCreateError((error as Error).message); return false; }
  }
  async function version(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget; const f = new FormData(form); const body = new FormData();
    setVersionError(""); body.append("arquivo", f.get("arquivo")!);
    try {
      await upload(`/api/v1/documentos/${versionDocumentId}/versoes`, body, token);
      form.reset(); dirty.current.version = false; setModal(null); setVersionDocumentId(""); setVersionSnapshot(null);
      notify("Nova versão imutável armazenada."); search();
    } catch (error) { setVersionError((error as Error).message); return false; }
  }
  async function deactivate(id: number) {
    if (!await confirm({ title: "Desativar documento?", message: `${items.find(item => item.id === id)?.titulo ?? "Este documento"} deixará de aceitar novas versões. O histórico continuará disponível para consulta e download.`,
      confirmLabel: "Desativar documento", destructive: true })) return;
    try {
      await request(`/api/v1/documentos/${id}`, { method: "DELETE" }, token);
      if (String(id) === versionDocumentId) {
        versionRoot.current?.querySelector("form")?.reset(); dirty.current.version = false; setVersionDocumentId(""); setVersionSnapshot(null);
      }
      notify("Documento desativado; as versões históricas foram preservadas."); search();
    } catch (error) { notify((error as Error).message, "error"); return false; }
  }

  return <div className="stack documents-page">
    {(linkLoading || linkError) && <section className="panel"><ResourceState loading={linkLoading} error={linkError} label="o vínculo do link" onRetry={() => setLinkAttempt(value => value + 1)} /></section>}
    {processContext && <div><button className="secondary-action" onClick={onReturn}>{contextTarget?.anexarAssinado ? "Voltar ao processo em preenchimento" : "Voltar ao Processo Administrativo"}</button></div>}
    <section className="panel document-scope"><div className="panel-title"><h2>Vínculo dos documentos</h2>
      {proprietario && <button className="secondary-action" aria-expanded={pickerOpen}
        ref={pickerToggle} onClick={() => setPickerOpen(value => !value)}>{pickerOpen ? "Recolher seleção" : "Trocar vínculo"}</button>}</div>
      {proprietario && <p className="document-context"><strong>{proprietario.numero}</strong>
        <span>{rotuloDominio(ownerType)} · {proprietario.origem}</span></p>}
      <div className="document-owner-picker" hidden={!pickerOpen && !!proprietario}>
      <label className="document-owner-type"><span id="document-owner-type-label">Vincular documento a</span><select name="proprietarioTipo" value={ownerType}
        aria-labelledby="document-owner-type-label" aria-describedby="document-owner-type-help"
        onChange={e => selecionarTipoProprietario(e.target.value)}>
        {opcoesDominio("proprietarioDocumento").map(opcao => <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}
      </select><small id="document-owner-type-help">Escolha se o arquivo pertence ao processo, ao instrumento ou a uma alteração.</small></label>
      <SeletorProprietarioDocumento key={ownerType} tipo={ownerType} token={token}
        selecionado={proprietario} onSelecionar={selecionarProprietario} />
      </div>
      {proprietarioSelecionadoInativo && <p className="muted">
        O Processo Administrativo está inativo. Os documentos permanecem disponíveis somente para consulta e download.
      </p>}
    </section>
    <section className="panel documents-library"><div className="panel-title"><h2>Documentos</h2>
      <div className="form-actions"><button className="secondary-action" onClick={() => search()} disabled={!ownerId}>Atualizar</button>
        <button className="primary" aria-haspopup="dialog" onClick={() => setModal("create")}
          disabled={!proprietarioSelecionadoAtivo}>Anexar documento</button></div></div>
      {!ownerId && <p className="empty">Escolha o vínculo acima para consultar os documentos ou anexar um arquivo.</p>}
      <ResourceState {...documents} onRetry={search} label="os documentos" />
      {ownerId && !documents.loading && !documents.error && !items.length && <p className="empty">Nenhum documento para o vínculo selecionado. Use Anexar documento para enviar o primeiro arquivo.</p>}
      {items.map(d => <article className="doc document-card" key={d.id}>
        <div className="document-header"><div><strong>#{d.id} · {d.titulo}</strong>
          <small>{rotuloDominio(d.categoria)} · {d.versoes.length} versão(ões) · criado por {d.criadoPor.nome}{!d.ativo && " · Inativo"}</small></div>
          <div className="document-actions">
            <button className="secondary-action" aria-haspopup="dialog" onClick={() => openVersion(d)}
              disabled={!proprietarioSelecionadoAtivo || !d.ativo || actions.pending.has(String(d.id))}>Nova versão</button>
            <button onClick={() => void actions.run(String(d.id), () => deactivate(d.id))}
              disabled={!proprietarioSelecionadoAtivo || !d.ativo || actions.pending.has(String(d.id))}>Desativar</button>
          </div></div>
        <div className="document-versions">{d.versoes.slice(0, 1).map(v => <DocumentVersion key={v.versao} documentId={d.id} version={v} token={token} notify={notify} />)}
          {d.versoes.length > 1 && <details className="document-history" open={expandedHistories[d.id] ?? false}
            onToggle={event => { const open = event.currentTarget.open; setExpandedHistories(current => current[d.id] === open ? current : { ...current, [d.id]: open }); }}><summary>Versões anteriores · {d.versoes.length - 1}</summary>
            {d.versoes.slice(1).map(v => <DocumentVersion key={v.versao} documentId={d.id} version={v} token={token} notify={notify} />)}</details>}
        </div>
      </article>)}
    </section>

    <Modal open={modal === "create" && active} title="Anexar documento" busy={createPending}
      description="O título e o arquivo são mantidos ao fechar esta janela, durante esta sessão."
      closeLabel="Fechar anexação" onClose={() => setModal(null)}>
      <div ref={createRoot} onChange={() => { dirty.current.create = true; }}>
        <p className="document-context"><strong>{rotuloDominio(ownerType)}</strong><span>{proprietario?.numero} · {proprietario?.origem}</span></p>
        <MutationForm formKey="document-create" className="stack" onSubmit={create}>
          <div className="feedback-live" role="alert" aria-atomic="true">{createError && <p className="error">{createError}</p>}</div>
          <label>Categoria<select name="categoria" value={category} onChange={e => setCategory(categoriaDocumento(e.target.value))}>
            {opcoesDominio("categoriaDocumento").map(opcao => <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}
          </select></label>
          <label>Título<input name="titulo" required disabled={!proprietarioSelecionadoAtivo} /></label>
          <label>Arquivo<input name="arquivo" type="file" key={category} accept={formatosCriacao.accept}
            aria-label="Arquivo" aria-describedby="document-create-formats"
            required disabled={!proprietarioSelecionadoAtivo} /><small id="document-create-formats">{formatosCriacao.ajuda}</small></label>
          <button className="primary" disabled={!proprietarioSelecionadoAtivo}>Anexar documento</button>
        </MutationForm>
      </div>
    </Modal>
    <Modal open={modal === "version" && active} title="Nova versão" busy={versionPending}
      description="O arquivo será acrescentado ao histórico. As versões anteriores permanecem disponíveis."
      closeLabel="Fechar nova versão" onClose={() => setModal(null)}>
      <div ref={versionRoot} onChange={() => { dirty.current.version = true; }}>
        <p className="document-context"><strong>{documentoVersionado?.titulo}</strong>
          <span>{documentoVersionado && `${rotuloDominio(documentoVersionado.categoria)} · versão atual ${documentoVersionado.versoes[0]?.versao}`}</span></p>
        <MutationForm formKey="document-version" className="stack" onSubmit={version}>
          <div className="feedback-live" role="alert" aria-atomic="true">{versionError && <p className="error">{versionError}</p>}</div>
          <label>Arquivo<input name="arquivo" type="file" key={`${versionDocumentId}:${documentoVersionado?.categoria}`}
            aria-label="Arquivo" aria-describedby="document-version-formats"
            accept={formatosNovaVersao?.accept} required disabled={!proprietarioSelecionadoAtivo || !versionAvailable} />
            <small id="document-version-formats">{formatosNovaVersao?.ajuda ?? "O documento não está disponível para novas versões."}</small></label>
          <button className="primary" disabled={!proprietarioSelecionadoAtivo || !versionAvailable}>Adicionar versão</button>
        </MutationForm>
      </div>
    </Modal>
  </div>;
}

function DocumentVersion({ documentId, version: v, token, notify }: {
  documentId: number; version: Documento["versoes"][number]; token: string; notify: AuthenticatedPageProps["notify"];
}) {
  return <div className="document-version"><div><strong>Versão {v.versao} · {v.nomeArquivo}</strong>
    <small>Enviada por {v.criadoPor.nome} · {v.tamanho < 1024 ? `${v.tamanho} bytes` : `${(v.tamanho / 1024).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} KB`}</small>
    <details><summary>Detalhes técnicos</summary><small className="checksum">SHA-256 · {v.checksumSha256}</small></details></div>
    <button onClick={() => void download(`/api/v1/documentos/${documentId}/versoes/${v.versao}/arquivo`, token)
      .catch(error => notify(`${v.nomeArquivo}: ${(error as Error).message} Tente baixar novamente.`, "error"))}>Baixar versão {v.versao}</button></div>;
}
