package com.moments.sicc.domain;

public final class Enums {

    private Enums() {
    }

    public enum PerfilAcesso { ADMINISTRADOR_DIPAC, OPERADOR_DIPAC }
    public enum TipoNotificacao {
        CHEGADA_TRAMITACAO, ALERTA_VIGENCIA_CONTRATUAL, ALERTA_VIGENCIA_TED
    }
    public enum ResultadoAuditoria { SUCESSO, FALHA }
    public enum StatusProcesso { EM_FORMALIZACAO, EM_VIGENCIA, CONCLUIDO }
    public enum TipoInstrumento {
        CONTRATO_GESTAO, CONVENIO, ACORDO_PARCERIA, ACORDO_COOPERACAO_TECNICA
    }
    public enum SituacaoVigencia { VALIDA, PROXIMA_VENCIMENTO, VENCIDA, NAO_INFORMADA }
    public enum ContextoTramitacao { FORMALIZACAO, TERMO_ADITIVO, APOSTILAMENTO }
    public enum ProprietarioDocumento { PROCESSO, INSTRUMENTO, TERMO_ADITIVO, APOSTILAMENTO }
    public enum CategoriaDocumento { ADMINISTRATIVO, ASSINADO }
    public enum TipoAlteracao {
        TERMO_ADITIVO(
                ContextoTramitacao.TERMO_ADITIVO,
                ProprietarioDocumento.TERMO_ADITIVO,
                "TERMO_ADITIVO",
                "Termo Aditivo"),
        APOSTILAMENTO(
                ContextoTramitacao.APOSTILAMENTO,
                ProprietarioDocumento.APOSTILAMENTO,
                "APOSTILAMENTO",
                "Apostilamento");

        private final ContextoTramitacao contextoTramitacao;
        private final ProprietarioDocumento proprietarioDocumento;
        private final String entidadeAuditoria;
        private final String nome;

        TipoAlteracao(
                ContextoTramitacao contextoTramitacao,
                ProprietarioDocumento proprietarioDocumento,
                String entidadeAuditoria,
                String nome) {
            this.contextoTramitacao = contextoTramitacao;
            this.proprietarioDocumento = proprietarioDocumento;
            this.entidadeAuditoria = entidadeAuditoria;
            this.nome = nome;
        }

        public ContextoTramitacao contextoTramitacao() {
            return contextoTramitacao;
        }

        public ProprietarioDocumento proprietarioDocumento() {
            return proprietarioDocumento;
        }

        public String entidadeAuditoria() {
            return entidadeAuditoria;
        }

        public String nome() {
            return nome;
        }
    }
    public enum EstadoAlteracao { RASCUNHO, EFETIVADA }
    public enum OperacaoAlteracao { ORIGINAL, RETIFICACAO, CANCELAMENTO }
    public enum CampoInstrumento {
        OBJETO(false), DESCRICAO(false), NATUREZA(false), COORDENADOR(true), PARTICIPES(false),
        VALOR_ATUAL(false), VIGENCIA_CONTRATUAL_FINAL(false), VIGENCIA_TED_FINAL(true);

        private final boolean permitidoEmApostilamento;

        CampoInstrumento(boolean permitidoEmApostilamento) {
            this.permitidoEmApostilamento = permitidoEmApostilamento;
        }

        public boolean permitidoEmApostilamento() {
            return permitidoEmApostilamento;
        }
    }
    public enum TipoRelatorio {
        ANUAL_PROCESSOS, INSTRUMENTOS_POR_TIPO, HISTORICO_TRAMITACOES, VIGENCIAS, CONSOLIDADO
    }
    public enum FormatoRelatorio { PDF, XLSX, CSV }
}
