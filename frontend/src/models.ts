export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

import type {
  CampoInstrumento,
  CategoriaDocumento,
  EstadoAlteracao,
  FormatoRelatorio,
  TipoRelatorio,
  OperacaoAlteracao,
  PerfilAcesso,
  ResultadoAuditoria,
  SituacaoVigencia,
  StatusProcesso,
  TipoAlteracao,
  TipoInstrumento,
  TipoNotificacao,
  TipoProprietarioDocumento
} from "./domain";

export type {
  CampoInstrumento,
  CategoriaDocumento,
  SituacaoVigencia,
  StatusProcesso,
  TipoInstrumento,
  TipoProprietarioDocumento
} from "./domain";

export type ProcessoAdministrativo = {
  id: number;
  numero: string;
  origem: string;
  numeroProjeto?: string;
  status: StatusProcesso;
  ativo: boolean;
  responsavel?: ResponsavelProcesso;
  setorAtual?: string;
  instrumento?: Instrumento;
};

export type Instrumento = {
  id: number;
  numero: string;
  tipo: TipoInstrumento;
  coordenador: string;
  valorAtual: number;
  objeto?: string;
  descricao?: string;
  natureza?: string;
  participes?: string[];
  vigenciaContratualFinal: string;
  vigenciaTedFinal?: string;
  documentoAssinadoId: number;
  documentoAssinadoVersao: number;
  documentoAssinadoChecksumSha256: string;
  situacaoContratual: SituacaoVigencia;
  situacaoTed: SituacaoVigencia;
};

export type Setor = { id: number; sigla: string; nome: string; ativo: boolean };

export type Movimentacao = {
  id: number;
  dataMovimentacao: string;
  sequenciaDiaria: number;
  setorDestino: Setor;
  autor: { id: number; login: string; nome: string };
  observacao?: string;
  inseridoEm: string;
};

export type PermanenciaSetor = {
  setor: Setor;
  dataChegada: string;
  dataSaida?: string;
  diasCorridos: number;
  aberta: boolean;
};

export type HistoricoTramitacao = {
  setorAtual?: Setor | null;
  movimentacoes: Movimentacao[];
  permanencias: PermanenciaSetor[];
};

export type MudancaAlteracao = {
  campo: CampoInstrumento;
  valorAnterior?: string | null;
  valorNovo?: string | null;
};

export type EstadoAtualInstrumento = {
  objeto: string;
  descricao?: string | null;
  natureza: string;
  coordenador: string;
  participes: string[];
  valorAtual: number;
  vigenciaContratualFinal: string;
  vigenciaTedFinal?: string | null;
  statusProcesso: StatusProcesso;
  precedenciaPorCampo?: Partial<Record<CampoInstrumento, { dataEfetivacao: string; ordemOficial: number }>>;
};

export type AlteracaoVinculada = {
  id: number;
  numeroOficial: string;
  tipo: TipoAlteracao;
  estado: EstadoAlteracao;
  operacao: OperacaoAlteracao;
  referenciaId?: number | null;
  dataEfetivacao?: string | null;
  ordemOficial?: number | null;
  produzEfeitoAtual: boolean;
  valoresProduzidos: Partial<Record<CampoInstrumento, string | null>>;
};

export type AlteracaoContratual = {
  id: number;
  instrumentoId: number;
  tipo: TipoAlteracao;
  estado: EstadoAlteracao;
  numeroOficial: string;
  dataEfetivacao?: string | null;
  ordemOficial?: number | null;
  operacao: OperacaoAlteracao;
  referenciaId?: number | null;
  documentoAssinadoId?: number | null;
  mudancas: MudancaAlteracao[];
  estadoAtualInstrumento: EstadoAtualInstrumento;
  tramitacao: HistoricoTramitacao;
  cadeia?: AlteracaoVinculada[];
};

export type ResponsavelProcesso = {
  id: number;
  nome: string;
  perfil: PerfilAcesso;
};

export type AutorDocumento = { id: number; nome: string };

export type VersaoDocumento = {
  versao: number;
  nomeArquivo: string;
  tipoMime: string;
  tamanho: number;
  checksumSha256: string;
  criadoPor: AutorDocumento;
  criadoEm: string;
};

export type Documento = {
  id: number;
  titulo: string;
  categoria: CategoriaDocumento;
  ativo: boolean;
  criadoPor: AutorDocumento;
  criadoEm: string;
  versoes: VersaoDocumento[];
};

export type Publico = {
  numeroProcesso: string;
  tipoInstrumento: TipoInstrumento | "Ainda não formalizado";
  origem: string;
  coordenador: string;
  status: StatusProcesso;
  vigenciaContratualFinal?: string;
  vigenciaTedFinal?: string;
};

export type RegistroAuditoria = {
  id: number;
  acao: string;
  resultado: ResultadoAuditoria;
  ator: { id: number; login: string; nome: string } | null;
  objeto: { tipo: string; id: number | null };
  detalhes?: string;
  ipOrigem?: string;
  criadoEm: string;
};

export type NotificacaoInterna = {
  id: number;
  mensagem: string;
  tipo: TipoNotificacao;
  processoId: number | null;
  lida: boolean;
  criadaEm: string;
};

export type DashboardData = {
  processosPorStatus: Record<StatusProcesso, number>;
  percentualConcluidos: number;
  alertasContratuais: number;
  alertasTed: number;
  valorTotalVigente: number;
  instrumentosPorTipo: Record<TipoInstrumento, number>;
  permanenciaMediaPorSetor: Record<string, number>;
  maiorGargalo: string | null;
  detalhesPermanenciaPorSetor: Record<string, {
    processoId: number;
    numeroProcesso: string;
    dataChegada: string;
    dataSaida?: string | null;
    diasCorridos: number;
    aberta: boolean;
  }[]>;
  tempoMedioTramitacaoInicialDias: number;
  detalhesTempoTramitacaoInicial: {
    processoId: number;
    numeroProcesso: string;
    dataCadastro: string;
    dataFormalizacao?: string | null;
    diasCorridos: number;
    aberta: boolean;
  }[];
  formalizacoesMensais: Record<string, number>;
  conclusoesMensais: Record<string, number>;
};

export type DadosAtuaisInstrumento = {
  objeto?: string | null;
  descricao?: string | null;
  natureza?: string | null;
  coordenador: string;
  participes?: string[] | null;
  valorAtual: number;
  vigenciaContratualFinal: string;
  vigenciaTedFinal?: string | null;
};

export type AuthenticatedPageProps = { token: string; notify: (message: string) => void };

export type RelatorioGerado = {
  id: number; tipo: TipoRelatorio; formato: FormatoRelatorio; filtros: Record<string, string>;
  criadoPor: { id: number; login: string; nome: string }; criadoEm: string;
  checksumSha256: string | null; chaveArmazenamento: string; tamanhoBytes: number;
  nomeArquivo: string;
};

export type ProprietarioDocumento = {
  id: number;
  numero: string;
  numeroProcesso: string;
  origem: string;
  numeroInstrumento: string | null;
  tipoInstrumento: TipoInstrumento | null;
  estadoAlteracao: EstadoAlteracao | null;
  statusProcesso: StatusProcesso;
  processoAtivo: boolean;
};
