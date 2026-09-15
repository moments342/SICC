export const TIPOS_INSTRUMENTO = [
  "CONTRATO_GESTAO",
  "CONVENIO",
  "ACORDO_PARCERIA",
  "ACORDO_COOPERACAO_TECNICA"
] as const;
export type TipoInstrumento = typeof TIPOS_INSTRUMENTO[number];

export const STATUS_PROCESSO = ["EM_FORMALIZACAO", "EM_VIGENCIA", "CONCLUIDO"] as const;
export type StatusProcesso = typeof STATUS_PROCESSO[number];

export const SITUACOES_VIGENCIA = ["VALIDA", "PROXIMA_VENCIMENTO", "VENCIDA", "NAO_INFORMADA"] as const;
export type SituacaoVigencia = typeof SITUACOES_VIGENCIA[number];

export const PERFIS_ACESSO = ["OPERADOR_DIPAC", "ADMINISTRADOR_DIPAC"] as const;
export type PerfilAcesso = typeof PERFIS_ACESSO[number];

export const CONTEXTOS_TRAMITACAO = ["FORMALIZACAO", "TERMO_ADITIVO", "APOSTILAMENTO"] as const;
export type ContextoTramitacao = typeof CONTEXTOS_TRAMITACAO[number];

export const TIPOS_PROPRIETARIO_DOCUMENTO = ["PROCESSO", "INSTRUMENTO", "TERMO_ADITIVO", "APOSTILAMENTO"] as const;
export type TipoProprietarioDocumento = typeof TIPOS_PROPRIETARIO_DOCUMENTO[number];

export const CATEGORIAS_DOCUMENTO = ["ADMINISTRATIVO", "ASSINADO"] as const;
export type CategoriaDocumento = typeof CATEGORIAS_DOCUMENTO[number];

export const TIPOS_ALTERACAO = ["TERMO_ADITIVO", "APOSTILAMENTO"] as const;
export type TipoAlteracao = typeof TIPOS_ALTERACAO[number];

export const ESTADOS_ALTERACAO = ["RASCUNHO", "EFETIVADA"] as const;
export type EstadoAlteracao = typeof ESTADOS_ALTERACAO[number];

export const OPERACOES_ALTERACAO = ["ORIGINAL", "RETIFICACAO", "CANCELAMENTO"] as const;
export type OperacaoAlteracao = typeof OPERACOES_ALTERACAO[number];

export const CAMPOS_INSTRUMENTO = [
  "OBJETO",
  "DESCRICAO",
  "NATUREZA",
  "COORDENADOR",
  "PARTICIPES",
  "VALOR_ATUAL",
  "VIGENCIA_CONTRATUAL_FINAL",
  "VIGENCIA_TED_FINAL"
] as const;
export type CampoInstrumento = typeof CAMPOS_INSTRUMENTO[number];

export const CAMPOS_APOSTILAMENTO = ["COORDENADOR", "VIGENCIA_TED_FINAL"] as const satisfies readonly CampoInstrumento[];

export const TIPOS_NOTIFICACAO = [
  "CHEGADA_TRAMITACAO",
  "ALERTA_VIGENCIA_CONTRATUAL",
  "ALERTA_VIGENCIA_TED"
] as const;
export type TipoNotificacao = typeof TIPOS_NOTIFICACAO[number];

export const RESULTADOS_AUDITORIA = ["SUCESSO", "FALHA"] as const;
export type ResultadoAuditoria = typeof RESULTADOS_AUDITORIA[number];

export const TIPOS_RELATORIO = [
  "ANUAL_PROCESSOS", "INSTRUMENTOS_POR_TIPO", "HISTORICO_TRAMITACOES", "VIGENCIAS", "CONSOLIDADO"
] as const;
export type TipoRelatorio = typeof TIPOS_RELATORIO[number];

export const FORMATOS_RELATORIO = ["PDF", "XLSX", "CSV"] as const;
export type FormatoRelatorio = typeof FORMATOS_RELATORIO[number];
