package com.moments.sicc.service;

import java.util.Optional;

public final class PoliticaSenha {
    private static final String REQUISITOS =
            "A senha deve ter ao menos 10 caracteres, com maiúscula, minúscula e número.";

    private PoliticaSenha() {}

    public static Optional<String> motivoInvalidez(String senha) {
        if (senha == null || senha.length() < 10
                || senha.chars().noneMatch(Character::isUpperCase)
                || senha.chars().noneMatch(Character::isLowerCase)
                || senha.chars().noneMatch(Character::isDigit)) {
            return Optional.of(REQUISITOS);
        }
        return Optional.empty();
    }
}
