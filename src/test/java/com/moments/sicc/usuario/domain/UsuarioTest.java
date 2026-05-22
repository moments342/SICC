package com.moments.sicc.usuario.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moments.sicc.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

class UsuarioTest {

    @Test
    void autenticarRetornaTrueQuandoUsuarioAtivoESenhaConfere() {
        Usuario usuario = usuario("ADMINISTRADOR");

        assertThat(usuario.autenticar("senha123")).isTrue();
        assertThat(usuario.getUltimoAcesso()).isNotNull();
        assertThat(usuario.getSenhaHash()).isNotEqualTo("senha123");
        assertThat(usuario.getSenhaHash()).hasSize(64);
    }

    @Test
    void autenticarRetornaFalseQuandoUsuarioEstaInativo() {
        Usuario usuario = usuario("ADMINISTRADOR");
        usuario.desativar();

        assertThat(usuario.autenticar("senha123")).isFalse();
    }

    @Test
    void alterarSenhaExigeSenhaAtualCorreta() {
        Usuario usuario = usuario("ADMINISTRADOR");

        assertThatThrownBy(() -> usuario.alterarSenha("errada", "novaSenha"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Senha atual invalida");
    }

    @Test
    void alterarSenhaSubstituiSenhaAtualPelaNova() {
        Usuario usuario = usuario("ADMINISTRADOR");

        usuario.alterarSenha("senha123", "novaSenha");

        assertThat(usuario.autenticar("senha123")).isFalse();
        assertThat(usuario.autenticar("novaSenha")).isTrue();
    }

    @Test
    void temPermissaoPermiteTodasAcoesParaAdministrador() {
        Usuario usuario = usuario("ADMINISTRADOR");

        assertThat(usuario.temPermissao("GERENCIAR_USUARIOS")).isTrue();
        assertThat(usuario.temPermissao("TRAMITAR_PROCESSO")).isTrue();
    }

    @Test
    void temPermissaoLimitaConsultaPublicaParaPublicoExterno() {
        Usuario usuario = usuario("PUBLICO_EXTERNO");

        assertThat(usuario.temPermissao("CONSULTAR_PUBLICO")).isTrue();
        assertThat(usuario.temPermissao("TRAMITAR_PROCESSO")).isFalse();
    }

    private Usuario usuario(String perfil) {
        Usuario usuario = new Usuario();
        usuario.setNome("Gabriel");
        usuario.setEmail("gabriel@sicc.test");
        usuario.setLogin("gabriel");
        usuario.definirSenha("senha123");
        usuario.setPerfil(perfil);
        return usuario;
    }
}
