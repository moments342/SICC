package com.moments.sicc.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.domain.UsuarioInterno;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void chaveJwtCurtaImpedeInicializacao() {
        assertThatThrownBy(() -> new JwtService(
                new ObjectMapper(), "chave-curta", 3600, relogioEm("2026-08-30T12:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SICC_JWT_SECRET")
                .hasMessageContaining("32 bytes");
    }

    @Test
    void tokenExpiraExatamenteNoInstanteCalculadoPeloRelogioInjetado() {
        String segredo = "chave-de-testes-com-mais-de-trinta-e-dois-bytes";
        JwtService emissor = new JwtService(
                new ObjectMapper(), segredo, 60, relogioEm("2026-08-30T12:00:00Z"));
        UsuarioInterno admin = new UsuarioInterno();
        admin.setLogin("admin");
        admin.setPerfil(PerfilAcesso.ADMINISTRADOR_DIPAC);

        String token = emissor.gerar(admin);
        JwtService validadorNoLimite = new JwtService(
                new ObjectMapper(), segredo, 60, relogioEm("2026-08-30T12:01:00Z"));

        assertThatThrownBy(() -> validadorNoLimite.validar(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token expirado.");
    }

    private Clock relogioEm(String instante) {
        return Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
    }
}
