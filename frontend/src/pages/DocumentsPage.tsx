import { FormEvent, useCallback, useEffect, useState } from "react";
import { download, request, upload } from "../api";
import { categoriaDocumento, formatosPermitidosDocumento } from "../documentCatalog";
import { opcoesDominio, rotuloDominio } from "../domainLabels";
import type {
  ProprietarioDocumento,
  AuthenticatedPageProps,
  CategoriaDocumento,
  Documento,
  TipoProprietarioDocumento
} from "../models";
import { SeletorProprietarioDocumento } from "../components/documents/SeletorProprietarioDocumento";

export function Documents({ token, notify }: AuthenticatedPageProps) {
  const [ownerType, setOwnerType] = useState<TipoProprietarioDocumento>("PROCESSO");
  const [proprietario, setProprietario] = useState<ProprietarioDocumento | null>(null);
  const ownerId = proprietario ? String(proprietario.id) : "";
  const [category, setCategory] = useState<CategoriaDocumento>("ADMINISTRATIVO");
  const [versionDocumentId, setVersionDocumentId] = useState("");
  const [items, setItems] = useState<Documento[]>([]);
  const formatosCriacao = formatosPermitidosDocumento(category);
  const documentosVersionaveis = items.filter(item => item.ativo);
  const documentoVersionado = documentosVersionaveis.find(
    item => String(item.id) === versionDocumentId);
  const formatosNovaVersao = documentoVersionado
    ? formatosPermitidosDocumento(documentoVersionado.categoria)
    : null;

  const proprietarioSelecionadoAtivo = proprietario?.processoAtivo === true;
  const proprietarioSelecionadoInativo = proprietario?.processoAtivo === false;

  const search = useCallback(async (signal?: AbortSignal) => {
    if (!ownerId) {
      setItems([]);
      return;
    }
    try {
      setItems(await request<Documento[]>(
        `/api/v1/documentos?proprietarioTipo=${ownerType}&proprietarioId=${ownerId}&incluirInativos=true`,
        { signal }, token
      ));
    } catch (e) {
      if (!signal?.aborted) notify((e as Error).message);
    }
  }, [notify, ownerId, ownerType, token]);

  useEffect(() => {
    const controller = new AbortController();
    setVersionDocumentId("");
    setItems([]);
    void search(controller.signal);
    return () => controller.abort();
  }, [search]);

  function selecionarTipoProprietario(valor: string) {
    setOwnerType(valor as TipoProprietarioDocumento);
    setProprietario(null);
    setItems([]);
    setVersionDocumentId("");
  }
  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget); const body = new FormData();
    ["proprietarioTipo", "proprietarioId", "categoria", "titulo"].forEach(k => body.append(k, String(f.get(k))));
    body.append("arquivo", f.get("arquivo")!);
    try { await upload("/api/v1/documentos", body, token); notify("Documento e primeira versão armazenados."); await search(); }
    catch (error) { notify((error as Error).message); }
  }
  async function version(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const f = new FormData(e.currentTarget); const body = new FormData();
    body.append("arquivo", f.get("arquivo")!);
    try { await upload(`/api/v1/documentos/${f.get("documento")}/versoes`, body, token);
      notify("Nova versão imutável armazenada."); await search(); }
    catch (error) { notify((error as Error).message); }
  }
  async function deactivate(id: number) {
    try { await request(`/api/v1/documentos/${id}`, { method: "DELETE" }, token);
      notify("Documento desativado; as versões históricas foram preservadas."); await search(); }
    catch (error) { notify((error as Error).message); }
  }
  return <div className="grid two"><section className="panel"><h2>Novo documento</h2><form className="stack" onSubmit={create}>
    <label>Tipo de proprietário<select name="proprietarioTipo" value={ownerType}
      onChange={e => selecionarTipoProprietario(e.target.value)}>
      {opcoesDominio("proprietarioDocumento").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select>
      <small>Associe o documento ao objeto administrativo correspondente.</small></label>
    <SeletorProprietarioDocumento key={ownerType} tipo={ownerType} token={token}
      selecionado={proprietario} onSelecionar={setProprietario} />
    {proprietarioSelecionadoInativo && <p className="muted">
      O Processo Administrativo está inativo. Os documentos permanecem disponíveis somente para consulta e download.
    </p>}
    <label>Categoria<select name="categoria" value={category}
      onChange={e => setCategory(categoriaDocumento(e.target.value))}>
      {opcoesDominio("categoriaDocumento").map(opcao =>
        <option key={opcao.codigo} value={opcao.codigo}>{opcao.rotulo}</option>)}</select></label>
    <label>Título<input name="titulo" required disabled={!proprietarioSelecionadoAtivo} /></label>
    <label>Arquivo<input name="arquivo" type="file" key={category} accept={formatosCriacao.accept}
      required disabled={!proprietarioSelecionadoAtivo} /><small>{formatosCriacao.ajuda}</small></label>
    <button className="primary" disabled={!proprietarioSelecionadoAtivo}>Armazenar versão 1</button></form>
    <hr /><h2>Nova versão</h2>
    <form className="stack" onSubmit={version}><label>Documento para nova versão<select name="documento" required
      value={versionDocumentId} onChange={e => setVersionDocumentId(e.target.value)}
      disabled={!proprietarioSelecionadoAtivo || !documentosVersionaveis.length}>
      <option value="">Selecione</option>{documentosVersionaveis.map(documento => <option key={documento.id} value={documento.id}>
        {documento.titulo} · {rotuloDominio(documento.categoria)} · versão {documento.versoes[0]?.versao ?? "não informada"}
      </option>)}</select></label>
      <label>Arquivo<input name="arquivo" type="file"
        key={`${versionDocumentId}:${documentoVersionado?.categoria ?? "DESCONHECIDO"}`}
        accept={formatosNovaVersao?.accept} required={documentoVersionado !== undefined}
        disabled={!proprietarioSelecionadoAtivo || documentoVersionado === undefined} />
        <small>{formatosNovaVersao?.ajuda
          ?? "Selecione um documento da lista para liberar os formatos da nova versão."}</small></label>
      <button className="primary"
        disabled={!proprietarioSelecionadoAtivo || documentoVersionado === undefined}>Adicionar versão</button>
    </form></section>
    <section className="panel"><div className="panel-title"><h2>Documentos</h2>
      <button onClick={() => void search()} disabled={!ownerId}>Atualizar</button></div>
      {items.map(d => <article className="doc document-card" key={d.id}>
        <div className="document-header"><div><strong>#{d.id} · {d.titulo}</strong>
          <small>{rotuloDominio(d.categoria)} · {d.versoes.length} versão(ões) · criado por {d.criadoPor.nome}
            {!d.ativo && " · Inativo"}</small></div>
          <button onClick={() => deactivate(d.id)}
            disabled={!proprietarioSelecionadoAtivo || !d.ativo}>Desativar</button></div>
        <div className="document-versions">
          {d.versoes.map(v => <div className="document-version" key={v.versao}>
            <div><strong>Versão {v.versao} · {v.nomeArquivo}</strong>
              <small>Enviada por {v.criadoPor.nome} · {v.tamanho.toLocaleString("pt-BR")} bytes</small>
              <small className="checksum">SHA-256 · {v.checksumSha256}</small></div>
            <button onClick={() => download(
              `/api/v1/documentos/${d.id}/versoes/${v.versao}/arquivo`, token
            )}>Baixar versão {v.versao}</button>
          </div>)}
        </div>
      </article>)}</section></div>;
}
