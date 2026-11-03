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

    enum Papel { DADOS, CABECALHO, SECAO, NOTA, METADADO, TITULO }

    record Linha(List<Celula> celulas, Papel papel) {
        Linha(List<Celula> celulas) {
            this(celulas, Papel.DADOS);
        }

        Linha {
            celulas = List.copyOf(celulas);
            Objects.requireNonNull(papel);
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
                    : new Celula(valor.toString(), Natureza.NUMERO);
        }

        static Celula moeda(java.math.BigDecimal valor) {
            return valor == null ? vazia() : new Celula(valor.toPlainString(), Natureza.MOEDA);
        }

        static Celula percentual(double valor) {
            return new Celula(java.math.BigDecimal.valueOf(valor).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), Natureza.PERCENTUAL);
        }

        static Celula data(LocalDate valor) {
            return valor == null
                    ? vazia()
                    : new Celula(valor.toString(), Natureza.DATA);
        }

        static Celula dataHora(LocalDateTime valor) {
            return valor == null
                    ? vazia()
                    : new Celula(
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(valor),
                            Natureza.DATA_HORA);
        }

        static Celula enumeracao(Enum<?> valor) {
            return valor == null
                    ? vazia()
                    : new Celula(valor.toString(), Natureza.ENUMERACAO);
        }

        static Celula logico(Boolean valor) {
            return valor == null
                    ? vazia()
                    : new Celula(valor.toString(), Natureza.LOGICO);
        }

        static Celula vazia() {
            return new Celula("", Natureza.VAZIA);
        }

        String valor() {
            return valor;
        }

        boolean textual() {
            return natureza == Natureza.TEXTO;
        }

        Natureza natureza() {
            return natureza;
        }

        enum Natureza {
            TEXTO, NUMERO, MOEDA, PERCENTUAL, DATA, DATA_HORA, ENUMERACAO, LOGICO, VAZIA
        }
    }

    static final class Construtor {
        private final List<Linha> linhas = new ArrayList<>();

        Construtor linha(Celula... celulas) {
            linhas.add(new Linha(Arrays.asList(celulas)));
            return this;
        }

        Construtor titulo(Celula... celulas) {
            linhas.add(new Linha(Arrays.asList(celulas), Papel.TITULO));
            return this;
        }

        Construtor metadado(Celula... celulas) {
            linhas.add(new Linha(Arrays.asList(celulas), Papel.METADADO));
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
