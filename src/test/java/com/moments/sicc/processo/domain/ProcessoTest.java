package com.moments.sicc.processo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moments.sicc.shared.exception.DomainException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProcessoTest {

    @Test
    void cadastrarPreencheDefaultsQuandoDadosObrigatoriosSaoValidos() {
        Processo processo = processoValido();

        processo.cadastrar();

        assertThat(processo.isAtivo()).isTrue();
        assertThat(processo.getStatusAtual()).isEqualTo("CADASTRADO");
        assertThat(processo.getDataCadastro()).isNotNull();
    }

    @Test
    void validarCamposObrigatoriosExigeNumeroProcesso() {
        Processo processo = processoValido();
        processo.setNumeroProcesso(" ");

        assertThatThrownBy(processo::validarCamposObrigatorios)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Numero do processo");
    }

    @Test
    void atualizarStatusEEtapaAlteraAndamentoAtual() {
        Processo processo = processoValido();

        processo.atualizarStatus("EM_TRAMITACAO");
        processo.atualizarEtapa("Analise juridica");

        assertThat(processo.getStatusAtual()).isEqualTo("EM_TRAMITACAO");
        assertThat(processo.getEtapaAtual()).isEqualTo("Analise juridica");
    }

    @Test
    void verificaDuplicidadeComparandoNumeroDoProcesso() {
        Processo processo = processoValido();

        assertThat(processo.verificarDuplicidade("23005.000001/2026-10")).isTrue();
        assertThat(processo.verificarDuplicidade("23005.000002/2026-10")).isFalse();
    }

    @Test
    void estaVencidoQuandoVigenciaContratoPassou() {
        Processo processo = processoValido();
        processo.setVigenciaContrato(LocalDate.now().minusDays(1));

        assertThat(processo.estaVencido()).isTrue();
    }

    @Test
    void estaProximoDoVencimentoQuandoVigenciaEstaDentroDeTrintaDias() {
        Processo processo = processoValido();
        processo.setVigenciaTed(LocalDate.now().plusDays(10));

        assertThat(processo.estaProximoDoVencimento()).isTrue();
    }

    static Processo processoValido() {
        Processo processo = new Processo();
        processo.setNumeroProcesso("23005.000001/2026-10");
        processo.setTipoInstrumento("CONTRATO");
        processo.setObjeto("Contrato de apoio institucional");
        processo.setDescricao("Descricao do processo");
        return processo;
    }
}
