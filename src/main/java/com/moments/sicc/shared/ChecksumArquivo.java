package com.moments.sicc.shared;

import java.security.MessageDigest;
import java.util.HexFormat;

public final class ChecksumArquivo {

    private ChecksumArquivo() {
    }

    public static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível calcular o checksum SHA-256.", e);
        }
    }
}
