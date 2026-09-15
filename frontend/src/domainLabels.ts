import {
  CATEGORIAS_DOCUMENTO,
  CONTEXTOS_TRAMITACAO,
  ESTADOS_ALTERACAO,
  OPERACOES_ALTERACAO,
  PERFIS_ACESSO,
  RESULTADOS_AUDITORIA,
  SITUACOES_VIGENCIA,
  STATUS_PROCESSO,
  TIPOS_ALTERACAO,
  TIPOS_INSTRUMENTO,
  TIPOS_NOTIFICACAO,
  TIPOS_PROPRIETARIO_DOCUMENTO
} from "./domain";

import { CATALOGO_RELATORIOS } from "./reportCatalog";

type OpcaoDominio = Readonly<{ codigo: string; rotulo: string }>;

function opcoes<const T extends readonly string[]>(
  codigos: T,
  rotulos: Record<T[number], string>
): ReadonlyArray<{ codigo: T[number]; rotulo: string }> {
  return codigos.map(codigo => ({ codigo, rotulo: rotulos[codigo as T[number]] }));
}

const catalogos = {
  tipoInstrumento: opcoes(TIPOS_INSTRUMENTO, {
    CONTRATO_GESTAO: "Contrato de gestão", CONVENIO: "Convênio",
    ACORDO_PARCERIA: "Acordo de parceria", ACORDO_COOPERACAO_TECNICA: "Acordo de cooperação técnica"
  }),
  statusProcesso: opcoes(STATUS_PROCESSO, {
    EM_FORMALIZACAO: "Em formalização", EM_VIGENCIA: "Em vigência", CONCLUIDO: "Concluído"
  }),
  situacaoVigencia: opcoes(SITUACOES_VIGENCIA, {
    VALIDA: "Válida", PROXIMA_VENCIMENTO: "Próxima do vencimento",
    VENCIDA: "Vencida", NAO_INFORMADA: "Não informada"
  }),
  perfilAcesso: opcoes(PERFIS_ACESSO, {
    OPERADOR_DIPAC: "Operador DIPAC", ADMINISTRADOR_DIPAC: "Administrador DIPAC"
  }),
  contextoTramitacao: opcoes(CONTEXTOS_TRAMITACAO, {
    FORMALIZACAO: "Formalização", TERMO_ADITIVO: "Termo aditivo", APOSTILAMENTO: "Apostilamento"
  }),
  proprietarioDocumento: opcoes(TIPOS_PROPRIETARIO_DOCUMENTO, {
    PROCESSO: "Processo administrativo", INSTRUMENTO: "Instrumento contratual",
    TERMO_ADITIVO: "Termo aditivo", APOSTILAMENTO: "Apostilamento"
  }),
  categoriaDocumento: opcoes(CATEGORIAS_DOCUMENTO, {
    ADMINISTRATIVO: "Administrativo", ASSINADO: "Assinado"
  }),
  tipoAlteracao: opcoes(TIPOS_ALTERACAO, {
    TERMO_ADITIVO: "Termo aditivo", APOSTILAMENTO: "Apostilamento"
  }),
  estadoAlteracao: opcoes(ESTADOS_ALTERACAO, {
    RASCUNHO: "Rascunho", EFETIVADA: "Efetivada"
  }),
  operacaoAlteracao: opcoes(OPERACOES_ALTERACAO, {
    ORIGINAL: "Original", RETIFICACAO: "Retificação", CANCELAMENTO: "Cancelamento"
  }),
  tipoRelatorio: CATALOGO_RELATORIOS,
  resultadoAuditoria: opcoes(RESULTADOS_AUDITORIA, { SUCESSO: "Sucesso", FALHA: "Falha" }),
  tipoNotificacao: opcoes(TIPOS_NOTIFICACAO, {
    CHEGADA_TRAMITACAO: "Chegada de tramitação",
    ALERTA_VIGENCIA_CONTRATUAL: "Alerta de vigência contratual",
    ALERTA_VIGENCIA_TED: "Alerta de vigência TED"
  })
} as const satisfies Record<string, readonly OpcaoDominio[]>;

export type CatalogoDominio = keyof typeof catalogos;

const rotulosConhecidos: Record<string, string> = {
  ATIVO: "Ativo",
  INATIVO: "Inativo",
  ALTERACAO_CONTRATUAL: "Alteração contratual",
  DOCUMENTO: "Documento",
  INSTRUMENTO_CONTRATUAL: "Instrumento contratual",
  LOGIN: "Login",
  MOVIMENTACAO: "Movimentação",
  PROCESSO_ADMINISTRATIVO: "Processo administrativo",
  RELATORIO: "Relatório",
  SETOR: "Setor",
  SISTEMA: "Sistema",
  USUARIO_INTERNO: "Usuário interno",
  TROCA_DE_SENHA_OBRIGATÓRIA: "Troca de senha obrigatória"
};

const rotulos = Object.fromEntries([
  ...Object.values(catalogos).flat().map(opcao => [opcao.codigo, opcao.rotulo]),
  ...Object.entries(rotulosConhecidos)
]);

const palavrasComAcento: Record<string, string> = {
  alteracao: "alteração",
  atualizacao: "atualização",
  cooperacao: "cooperação",
  criacao: "criação",
  gestao: "gestão",
  movimentacao: "movimentação",
  proxima: "próxima",
  relatorio: "relatório",
  senha: "senha",
  tecnico: "técnico",
  tecnica: "técnica",
  tramitacao: "tramitação",
  tramitacoes: "tramitações",
  usuario: "usuário",
  vigencia: "vigência"
};

export function opcoesDominio<K extends CatalogoDominio>(catalogo: K): typeof catalogos[K] {
  return catalogos[catalogo];
}

export function rotuloDominio(codigo: string): string {
  const conhecido = rotulos[codigo];
  if (conhecido) return conhecido;
  if (!codigo.includes("_")
    || !/^[A-ZÁÉÍÓÚÂÊÔÃÕÇ0-9]+(?:_[A-ZÁÉÍÓÚÂÊÔÃÕÇ0-9]+)*$/u.test(codigo)) return codigo;

  const palavras = codigo.toLocaleLowerCase("pt-BR").split("_").map(palavra =>
    palavrasComAcento[palavra] ?? palavra
  );
  if (!palavras.length) return codigo;
  return `${palavras[0].charAt(0).toLocaleUpperCase("pt-BR")}${palavras[0].slice(1)}${
    palavras.length > 1 ? ` ${palavras.slice(1).join(" ")}` : ""
  }`;
}
