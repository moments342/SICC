package com.moments.sicc.service;

import java.util.Set;

final class CelulaCsv {
    private static final Set<Character> INICIOS_DE_FORMULA = Set.of('=', '+', '-', '@');

    private CelulaCsv() {}

    static String textoSeguroParaCsv(String valor) {
        String normalizado = TextoCelulaRelatorio.emUmaCelula(valor);
        int primeiroConteudo = 0;
        while (primeiroConteudo < normalizado.length()
                && TextoCelulaRelatorio.eEspacoUnicode(
                        normalizado.charAt(primeiroConteudo))) {
            primeiroConteudo++;
        }
        if (primeiroConteudo < normalizado.length()
                && INICIOS_DE_FORMULA.contains(normalizado.charAt(primeiroConteudo))) {
            return "'" + normalizado;
        }
        return normalizado;
    }
}
