import { CATEGORIAS_DOCUMENTO } from "./domain";
import type { CategoriaDocumento } from "./domain";

const formatosDocumentoAdministrativo = {
  accept: ".pdf,.docx,.xlsx,.csv",
  ajuda: "PDF, DOCX, XLSX ou CSV · máximo de 20 MB"
};

const formatosDocumentoAssinado = {
  accept: ".pdf",
  ajuda: "PDF · máximo de 20 MB"
};

export function formatosPermitidosDocumento(categoria: CategoriaDocumento) {
  return categoria === "ADMINISTRATIVO" ? formatosDocumentoAdministrativo : formatosDocumentoAssinado;
}

export function categoriaDocumento(valor: string): CategoriaDocumento {
  if ((CATEGORIAS_DOCUMENTO as readonly string[]).includes(valor)) return valor as CategoriaDocumento;
  throw new Error("Categoria de documento inválida.");
}
