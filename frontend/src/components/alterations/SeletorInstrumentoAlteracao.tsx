import { useEffect, useState } from "react";
import { request } from "../../api";
import { rotuloDominio } from "../../domainLabels";
import type { OpcaoInstrumentoAlteracao, Page } from "../../models";
import { ResourceState } from "../Feedback";
import { useSessionDraft } from "../SessionDrafts";

type Props = {
  token: string;
  selecionado: OpcaoInstrumentoAlteracao | null;
  onSelecionar: (instrumento: OpcaoInstrumentoAlteracao | null) => void;
};

/** Mesmo padrão de Documentos; a consulta nunca altera a seleção nem os rascunhos. */
export function SeletorInstrumentoAlteracao({ token, selecionado, onSelecionar }: Props) {
  const [busca, setBusca] = useSessionDraft("alterations:instrument-search", "", false);
  const [consulta, setConsulta] = useSessionDraft("alterations:instrument-query", { busca: "", pagina: 0 }, false);
  const [resultado, setResultado] = useState<Page<OpcaoInstrumentoAlteracao> | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");
  const [tentativa, setTentativa] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setCarregando(true); setErro(""); setResultado(null);
    const params = new URLSearchParams({ busca: consulta.busca, page: String(consulta.pagina), size: "20" });
    void request<Page<OpcaoInstrumentoAlteracao>>(`/api/v1/alteracoes/instrumentos?${params}`,
      { signal: controller.signal }, token)
      .then(pagina => { if (!controller.signal.aborted) setResultado(pagina); })
      .catch(error => { if (!controller.signal.aborted) setErro((error as Error).message); })
      .finally(() => { if (!controller.signal.aborted) setCarregando(false); });
    return () => controller.abort();
  }, [token, consulta.busca, consulta.pagina, tentativa]);

  function consultar() {
    setConsulta({ busca: busca.trim(), pagina: 0 });
    setTentativa(value => value + 1);
  }
  const opcoes = resultado?.content ?? [];
  const selecionadoForaDaPagina = selecionado && !opcoes.some(item => item.id === selecionado.id);
  function rotulo(item: OpcaoInstrumentoAlteracao) {
    return `${item.numero} · ${rotuloDominio(item.tipo)} · Processo ${item.numeroProcesso}`;
  }
  return <div className="stack" role="group" aria-label="Catálogo de instrumentos">
    <div className="inline-form">
      <label>Buscar instrumento<input value={busca} onChange={event => setBusca(event.target.value)}
        placeholder="Número do instrumento, processo ou origem" onKeyDown={event => {
          if (event.key === "Enter") { event.preventDefault(); consultar(); }
        }} /></label>
      <button type="button" className="primary" onClick={consultar}>Buscar instrumentos</button>
    </div>
    <label>Instrumento Contratual<select value={selecionado?.id ?? ""}
      disabled={carregando || Boolean(erro)} onChange={event => onSelecionar(
        opcoes.find(item => String(item.id) === event.target.value) ?? null)}>
      <option value="">Selecione</option>
      {selecionadoForaDaPagina && <option value={selecionado.id}>{rotulo(selecionado)} · Selecionado</option>}
      {opcoes.map(item => <option key={item.id} value={item.id}>{rotulo(item)}</option>)}
    </select></label>
    <ResourceState loading={carregando} error={erro} label="instrumentos"
      onRetry={() => setTentativa(value => value + 1)} />
    {resultado && <>
      <small role="status">{resultado.totalElements === 0 ? "Nenhum instrumento encontrado."
        : `Página ${resultado.number + 1} de ${resultado.totalPages} · ${resultado.totalElements} instrumento(s)`}</small>
      <div className="pagination">
        <button type="button" aria-label="Página anterior de instrumentos" disabled={resultado.number === 0}
          onClick={() => setConsulta({ ...consulta, pagina: resultado.number - 1 })}>Anterior</button>
        <button type="button" aria-label="Próxima página de instrumentos"
          disabled={resultado.number + 1 >= resultado.totalPages}
          onClick={() => setConsulta({ ...consulta, pagina: resultado.number + 1 })}>Próxima</button>
      </div>
    </>}
    {selecionado && <small>Instrumento selecionado: {rotulo(selecionado)}. Buscar ou paginar mantém esta seleção e o preenchimento.</small>}
  </div>;
}
