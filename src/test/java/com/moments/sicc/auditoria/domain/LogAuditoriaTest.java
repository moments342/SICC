package com.moments.sicc.auditoria.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.usuario.domain.Usuario;
import org.junit.jupiter.api.Test;

class LogAuditoriaTest {

    @Test
    void registrarPreencheDataHoraEDetalhesQuandoAusentes() {
        LogAuditoria log = logValido();
        log.setDetalhes(null);

        log.registrar();

        assertThat(log.getDataHora()).isNotNull();
        assertThat(log.getDetalhes()).contains("gabriel executou CADASTRAR em Processo #1");
    }

    @Test
    void gerarDescricaoUsaSistemaQuandoNaoHaUsuario() {
        LogAuditoria log = logValido();
        log.setUsuario(null);

        assertThat(log.gerarDescricao()).isEqualTo("sistema executou CADASTRAR em Processo #1");
    }

    @Test
    void registrarExigeAcao() {
        LogAuditoria log = logValido();
        log.setAcao(null);

        assertThatThrownBy(log::registrar)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Acao de auditoria");
    }

    static LogAuditoria logValido() {
        Usuario usuario = new Usuario();
        usuario.setLogin("gabriel");

        LogAuditoria log = new LogAuditoria();
        log.setUsuario(usuario);
        log.setAcao("CADASTRAR");
        log.setEntidadeAfetada("Processo");
        log.setIdEntidadeAfetada(1L);
        log.setDetalhes("Cadastro do processo");
        return log;
    }
}
