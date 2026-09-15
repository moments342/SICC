package com.moments.sicc.domain;

import static com.moments.sicc.domain.Enums.ContextoTramitacao.APOSTILAMENTO;
import static com.moments.sicc.domain.Enums.ContextoTramitacao.TERMO_ADITIVO;
import static org.assertj.core.api.Assertions.assertThat;

import com.moments.sicc.domain.Enums.TipoAlteracao;
import org.junit.jupiter.api.Test;

class TipoAlteracaoTest {
    @Test
    void concentraOsMapeamentosDeCadaTipoDeAlteracao() {
        assertThat(TipoAlteracao.TERMO_ADITIVO.contextoTramitacao()).isEqualTo(TERMO_ADITIVO);
        assertThat(TipoAlteracao.TERMO_ADITIVO.proprietarioDocumento())
                .isEqualTo(Enums.ProprietarioDocumento.TERMO_ADITIVO);
        assertThat(TipoAlteracao.TERMO_ADITIVO.entidadeAuditoria())
                .isEqualTo("TERMO_ADITIVO");

        assertThat(TipoAlteracao.APOSTILAMENTO.contextoTramitacao()).isEqualTo(APOSTILAMENTO);
        assertThat(TipoAlteracao.APOSTILAMENTO.proprietarioDocumento())
                .isEqualTo(Enums.ProprietarioDocumento.APOSTILAMENTO);
        assertThat(TipoAlteracao.APOSTILAMENTO.entidadeAuditoria())
                .isEqualTo("APOSTILAMENTO");
    }
}
