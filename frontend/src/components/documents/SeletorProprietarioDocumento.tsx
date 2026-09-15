import { useEffect, useState } from "react";
import { request } from "../../api";
import { rotuloDominio } from "../../domainLabels";
import type { Page, ProprietarioDocumento, TipoProprietarioDocumento } from "../../models";

type Props = {
  tipo: TipoProprietarioDocumento;
  token: string;
  selecionado: ProprietarioDocumento | null;
  onSelecionar: (proprietario: ProprietarioDocumento | null) => void;
};

export function SeletorProprietarioDocumento({ tipo, token, selecionado, onSelecionar }: Props) {
  const [busca, setBusca] = useState("");
  const [consulta, setConsulta] = useState({ busca: "", pagina: 0 });
  const [resultado, setResultado] = useState<Page<ProprietarioDocumento> | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    setCarregando(true);
    setErro("");
    setResultado(null);
    const params = new URLSearchParams({ tipo, busca: consulta.busca, page: String(consulta.pagina),
      size: "20", incluirInativos: "true" });
    void request<Page<ProprietarioDocumento>>(`/api/v1/documentos/proprietarios?${params}`,
      { signal: controller.signal }, token).then(pagina => {
      if (!controller.signal.aborted) setResultado(pagina);
    }).catch(error => {
      if (!controller.signal.aborted) setErro((error as Error).message);
    }).finally(() => {
      if (!controller.signal.aborted) setCarregando(false);
    });
    return () => controller.abort();
  }, [tipo, token, consulta]);

  function consultar(pagina = 0, termo = busca.trim()) {
    onSelecionar(null);
    setConsulta({ busca: termo, pagina });
  }

  return <div className="stack">
    <label>Buscar proprietário<input value={busca} onChange={e => setBusca(e.target.value)}
      placeholder="Número, origem ou instrumento" onKeyDown={e => {
        if (e.key === "Enter") { e.preventDefault(); consultar(); }
      }} /></label>
    <button type="button" className="primary" onClick={() => consultar()}>Buscar proprietários</button>
    <label>Objeto proprietário<select name="proprietarioId" required
      disabled={carregando || Boolean(erro)} value={selecionado?.id ?? ""}
      onChange={e => onSelecionar(resultado?.content.find(item => String(item.id) === e.target.value) ?? null)}>
      <option value="">Selecione</option>
      {resultado?.content.map(item => <option key={item.id} value={item.id}>
        {rotuloProprietario(tipo, item)}
      </option>)}
    </select></label>
    {carregando && <small role="status">Carregando proprietários…</small>}
    {erro && <div role="alert" className="form-actions">{erro} <button type="button"
      onClick={() => consultar(consulta.pagina, consulta.busca)}>Tentar novamente</button></div>}
    {resultado && <div className="stack">
      <small role="status">{resultado.totalElements === 0 ? "Nenhum proprietário encontrado."
        : `Página ${resultado.number + 1} de ${resultado.totalPages} · ${resultado.totalElements} proprietário(s)`}</small>
      <div className="pagination">
      <button type="button" aria-label="Página anterior de proprietários"
        disabled={carregando || resultado.number === 0}
        onClick={() => consultar(resultado.number - 1, consulta.busca)}>Anterior</button>
      <button type="button" aria-label="Próxima página de proprietários"
        disabled={carregando || resultado.number + 1 >= resultado.totalPages}
        onClick={() => consultar(resultado.number + 1, consulta.busca)}>Próxima</button>
      </div>
    </div>}
  </div>;
}

function rotuloProprietario(tipo: TipoProprietarioDocumento, item: ProprietarioDocumento) {
  let descricao: string;
  switch (tipo) {
    case "PROCESSO": descricao = `${item.origem} · ${rotuloDominio(item.statusProcesso)}`; break;
    case "INSTRUMENTO": descricao = `${rotuloDominio(item.tipoInstrumento ?? "")} · Processo ${item.numeroProcesso}`; break;
    case "TERMO_ADITIVO":
    case "APOSTILAMENTO": descricao = `Instrumento ${item.numeroInstrumento} · ${rotuloDominio(item.estadoAlteracao ?? "")}`; break;
  }
  return `${item.numero} · ${descricao}${item.processoAtivo ? "" : " · Inativo"}`;
}
