package com.moments.sicc.service;

import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.service.RegrasDeVigencia.ReferenciaDeVigencia;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

final class EmissaoRelatorio {
    private final LocalDateTime geradoEm;
    private final ReferenciaDeVigencia referencia;
    private final String autorNome;
    private final String autorLogin;

    private EmissaoRelatorio(
            LocalDateTime geradoEm,
            ReferenciaDeVigencia referencia,
            String autorNome,
            String autorLogin) {
        this.geradoEm = geradoEm;
        this.referencia = referencia;
        this.autorNome = autorNome;
        this.autorLogin = autorLogin;
    }

    static EmissaoRelatorio iniciar(
            Clock clock, RegrasDeVigencia regrasDeVigencia, UsuarioInterno autor) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(regrasDeVigencia);
        Objects.requireNonNull(autor);
        LocalDateTime geradoEm = LocalDateTime.now(clock);
        return new EmissaoRelatorio(
                geradoEm,
                regrasDeVigencia.referenciaEm(geradoEm.toLocalDate()),
                Objects.requireNonNull(autor.getNome()),
                Objects.requireNonNull(autor.getLogin()));
    }

    LocalDateTime geradoEm() {
        return geradoEm;
    }

    LocalDate hoje() {
        return geradoEm.toLocalDate();
    }

    ReferenciaDeVigencia referencia() {
        return referencia;
    }

    String autorNome() {
        return autorNome;
    }

    String autorLogin() {
        return autorLogin;
    }
}
