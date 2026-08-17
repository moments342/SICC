package com.moments.sicc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.UsuarioInternoRepository;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BootstrapAdministradorTest {

    @Test
    void bancoVazioSemCredenciaisExternasImpedeInicializacao() {
        UsuarioInternoRepository usuarios = mock(UsuarioInternoRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        when(usuarios.count()).thenReturn(0L);
        BootstrapAdministrador bootstrap = new BootstrapAdministrador(
                usuarios,
                new BCryptPasswordEncoder(),
                auditoria,
                "",
                "",
                "",
                "Administrador SICC");

        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SICC_BOOTSTRAP_LOGIN")
                .hasMessageContaining("SICC_BOOTSTRAP_PASSWORD")
                .hasMessageContaining("SICC_BOOTSTRAP_EMAIL");
    }

    @ParameterizedTest
    @MethodSource("credenciaisInvalidas")
    void bancoVazioComCredenciaisInvalidasImpedeInicializacao(
            String login, String senha, String email, String nome, String campo) {
        UsuarioInternoRepository usuarios = mock(UsuarioInternoRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        when(usuarios.count()).thenReturn(0L);
        BootstrapAdministrador bootstrap = new BootstrapAdministrador(
                usuarios,
                new BCryptPasswordEncoder(),
                auditoria,
                login,
                senha,
                email,
                nome);

        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(campo);
    }

    @Test
    void bancoVazioCriaEAuditaPrimeiroAdministradorSemExporSenha() {
        UsuarioInternoRepository usuarios = mock(UsuarioInternoRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(usuarios.count()).thenReturn(0L);
        when(usuarios.save(any(UsuarioInterno.class))).thenAnswer(invocation -> {
            UsuarioInterno admin = invocation.getArgument(0);
            admin.setId(42L);
            return admin;
        });
        BootstrapAdministrador bootstrap = new BootstrapAdministrador(
                usuarios,
                encoder,
                auditoria,
                "Admin",
                "Temporaria123!",
                "ADMIN@sicc.test",
                "Administrador SICC");

        bootstrap.run(new DefaultApplicationArguments());

        ArgumentCaptor<UsuarioInterno> adminCaptor = ArgumentCaptor.forClass(UsuarioInterno.class);
        verify(usuarios).save(adminCaptor.capture());
        UsuarioInterno admin = adminCaptor.getValue();
        assertThat(admin.getLogin()).isEqualTo("admin");
        assertThat(admin.getEmail()).isEqualTo("admin@sicc.test");
        assertThat(admin.getPerfil()).isEqualTo(PerfilAcesso.ADMINISTRADOR_DIPAC);
        assertThat(admin.isSenhaTemporaria()).isTrue();
        assertThat(encoder.matches("Temporaria123!", admin.getSenhaHash())).isTrue();
        verify(auditoria).registrarNaTransacaoAtual(
                eq(admin),
                eq("CRIAR_USUARIO"),
                eq("USUARIO_INTERNO"),
                eq(42L),
                eq(true),
                eq("Primeiro Administrador DIPAC criado pelo bootstrap."),
                eq(null));
    }

    private static Stream<Arguments> credenciaisInvalidas() {
        return Stream.of(
                Arguments.of("ad min", "Temporaria123!", "admin@sicc.test", "Administrador", "SICC_BOOTSTRAP_LOGIN"),
                Arguments.of("admin", "fraca", "admin@sicc.test", "Administrador", "SICC_BOOTSTRAP_PASSWORD"),
                Arguments.of("admin", "Temporaria123!", "email-invalido", "Administrador", "SICC_BOOTSTRAP_EMAIL"),
                Arguments.of("admin", "Temporaria123!", "admin@sicc.test", " ", "SICC_BOOTSTRAP_NAME"));
    }
}
