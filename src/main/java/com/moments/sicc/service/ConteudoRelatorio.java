package com.moments.sicc.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

record ConteudoRelatorio(List<Linha> linhas) {

    ConteudoRelatorio {
        linhas = List.copyOf(linhas);
    }

    static Construtor construtor() {
        return new Construtor();
    }

    record Linha(List<Celula> celulas) {
        Linha {
            celulas = List.copyOf(celulas);
        }
    }

    static final class Celula {
        private final String valor;
        private final Natureza natureza;

        private Celula(String valor, Natureza natureza) {
            this.valor = valor == null ? "" : valor;
            this.natureza = natureza;
        }

        static Celula texto(String valor) {
            return new Celula(valor, Natureza.TEXTO);
        }

        static Celula numero(Number valor) {
            return valor == null
                    ? vazia()
                    : new Celula(valor.toString(), Natureza.VALOR);
        }

        static Celula data(LocalDate valor) {
            return valor == null
                    ? vazia()
                    : new Celula(valor.toString(), Natureza.VALOR);
        }

        static Celula dataHora(LocalDateTime valor) {
            return valor == null
                    ? vazia()
                    : new Celula(
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(valor),
                            Natureza.VALOR);
        }

        static Celula enumeracao(Enum<?> valor) {
            return valor == null
                    ? vazia()
                    : new Celula(valor.toString(), Natureza.VALOR);
        }

        static Celula logico(Boolean valor) {
            return valor == null
                    ? vazia()
                    : new Celula(valor.toString(), Natureza.VALOR);
        }

        static Celula vazia() {
            return new Celula("", Natureza.VALOR);
        }

        String valor() {
            return valor;
        }

        boolean textual() {
            return natureza == Natureza.TEXTO;
        }

        private enum Natureza {
            TEXTO,
            VALOR
        }
    }

    static final class Construtor {
        private final List<Linha> linhas = new ArrayList<>();

        Construtor linha(Celula... celulas) {
            linhas.add(new Linha(Arrays.asList(celulas)));
            return this;
        }

        Construtor adicionar(List<Linha> novasLinhas) {
            linhas.addAll(Objects.requireNonNull(novasLinhas));
            return this;
        }

        ConteudoRelatorio construir() {
            return new ConteudoRelatorio(linhas);
        }
    }
}
