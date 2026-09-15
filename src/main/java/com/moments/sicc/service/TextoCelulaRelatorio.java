package com.moments.sicc.service;

import java.util.regex.Pattern;

final class TextoCelulaRelatorio {
    private static final Pattern QUEBRAS_DE_CELULA = Pattern.compile("[\\r\\n\\t]+");

    private TextoCelulaRelatorio() {}

    static String emUmaCelula(String valor) {
        if (valor == null) return "";
        return QUEBRAS_DE_CELULA.matcher(valor).replaceAll(" ").replace(';', ',');
    }

    static boolean eEspacoUnicode(char caractere) {
        return Character.isWhitespace(caractere)
                || Character.isSpaceChar(caractere)
                || caractere == '\u0085';
    }
}
