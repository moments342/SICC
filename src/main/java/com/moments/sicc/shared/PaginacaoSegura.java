package com.moments.sicc.shared;

import com.moments.sicc.shared.exception.DomainException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PaginacaoSegura {
    public static final int TAMANHO_MAXIMO = 100;

    private PaginacaoSegura() {
    }

    public static PageRequest criar(int pagina, int tamanho, Sort ordenacao) {
        if (pagina < 0) {
            throw new DomainException("A página deve ser maior ou igual a zero.");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new DomainException("O tamanho da página deve estar entre 1 e 100.");
        }
        return PageRequest.of(pagina, tamanho, ordenacao);
    }
}
