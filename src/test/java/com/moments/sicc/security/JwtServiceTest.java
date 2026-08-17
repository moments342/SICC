package com.moments.sicc.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.domain.UsuarioInterno;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void chaveJwtCurtaImpedeInicializacao() {
        assertThatThrownBy(() -> new JwtService(new ObjectMapper(), "chave-curta", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SICC_JWT_SECRET")
                .hasMessageContaining("32 bytes");
    }

    @Test
    void tokenExpiradoERejeitado() {
        JwtService jwtService = new JwtService(
                new ObjectMapper(), "chave-de-testes-com-mais-de-trinta-e-dois-bytes", -1);
        UsuarioInterno admin = new UsuarioInterno();
        admin.setLogin("admin");
        admin.setPerfil(PerfilAcesso.ADMINISTRADOR_DIPAC);

        String token = jwtService.gerar(admin);

        assertThatThrownBy(() -> jwtService.validar(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token expirado.");
    }
}
