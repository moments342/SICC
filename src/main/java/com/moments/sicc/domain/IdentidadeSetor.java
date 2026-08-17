package com.moments.sicc.domain;

import java.util.Locale;

public final class IdentidadeSetor {
    private final String sigla;
    private final String nome;

    private IdentidadeSetor(String sigla, String nome) {
        this.sigla = sigla;
        this.nome = nome;
    }

    public static IdentidadeSetor de(String siglaInformada, String nomeInformado) {
        return new IdentidadeSetor(
                normalizar(siglaInformada).toUpperCase(Locale.ROOT),
                normalizar(nomeInformado));
    }

    public String sigla() {
        return sigla;
    }

    public String nome() {
        return nome;
    }

    public String siglaNormalizada() {
        return sigla.toLowerCase(Locale.ROOT);
    }

    public String nomeNormalizado() {
        return nome.toLowerCase(Locale.ROOT);
    }

    private static String normalizar(String valor) {
        return valor.trim().replaceAll("\\s+", " ");
    }
}
