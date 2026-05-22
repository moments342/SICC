package com.moments.sicc.tramitacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moments.sicc.processo.domain.Processo;
import com.moments.sicc.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

class TramitacaoTest {

    @Test
    void registrarPreencheDataDaMovimentacao() {
        Tramitacao tramitacao = tramitacaoValida();

        tramitacao.registrar();

        assertThat(tramitacao.getDataMovimentacao()).isNotNull();
    }

    @Test
    void aplicarAoProcessoAtualizaStatusEEtapa() {
        Processo processo = processoValido();
        Tramitacao tramitacao = tramitacaoValida();

        tramitacao.aplicarAoProcesso(processo);

        assertThat(tramitacao.getStatusAnterior()).isEqualTo("CADASTRADO");
        assertThat(processo.getStatusAtual()).isEqualTo("EM_ANALISE");
        assertThat(processo.getEtapaAtual()).isEqualTo("Analise administrativa");
    }

    @Test
    void aplicarAoProcessoInvalidaNaoAlteraProcesso() {
        Processo processo = processoValido();
        Tramitacao tramitacao = tramitacaoValida();
        tramitacao.setEtapa("");

        assertThatThrownBy(() -> tramitacao.aplicarAoProcesso(processo))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Etapa");

        assertThat(processo.getStatusAtual()).isEqualTo("CADASTRADO");
        assertThat(processo.getEtapaAtual()).isNull();
        assertThat(tramitacao.getStatusAnterior()).isNull();
    }

    @Test
    void registrarExigeStatusNovo() {
        Tramitacao tramitacao = tramitacaoValida();
        tramitacao.setStatusNovo(null);

        assertThatThrownBy(tramitacao::registrar)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Novo status");
    }

    @Test
    void resumoMovimentacaoRetornaResumoLegivel() {
        Tramitacao tramitacao = tramitacaoValida();

        assertThat(tramitacao.resumoMovimentacao())
                .isEqualTo("Analise administrativa - Encaminhado para analise por Maria");
    }

    private Processo processoValido() {
        Processo processo = new Processo();
        processo.setNumeroProcesso("23005.000001/2026-10");
        processo.setTipoInstrumento("CONTRATO");
        processo.setObjeto("Contrato de apoio institucional");
        processo.setStatusAtual("CADASTRADO");
        return processo;
    }

    static Tramitacao tramitacaoValida() {
        Tramitacao tramitacao = new Tramitacao();
        tramitacao.setSetor("DIPAC");
        tramitacao.setResponsavel("Maria");
        tramitacao.setAcaoRealizada("Encaminhado para analise");
        tramitacao.setEtapa("Analise administrativa");
        tramitacao.setStatusNovo("EM_ANALISE");
        return tramitacao;
    }
}
