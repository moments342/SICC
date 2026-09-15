package com.moments.sicc.service;

import static com.moments.sicc.domain.Enums.CampoInstrumento.COORDENADOR;
import static com.moments.sicc.domain.Enums.CampoInstrumento.DESCRICAO;
import static com.moments.sicc.domain.Enums.CampoInstrumento.NATUREZA;
import static com.moments.sicc.domain.Enums.CampoInstrumento.OBJETO;
import static com.moments.sicc.domain.Enums.CampoInstrumento.PARTICIPES;
import static com.moments.sicc.domain.Enums.CampoInstrumento.VALOR_ATUAL;
import static com.moments.sicc.domain.Enums.CampoInstrumento.VIGENCIA_CONTRATUAL_FINAL;
import static com.moments.sicc.domain.Enums.CampoInstrumento.VIGENCIA_TED_FINAL;

import com.moments.sicc.domain.Enums.CampoInstrumento;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.InstrumentoEstadoInicial;
import com.moments.sicc.shared.exception.DomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class CatalogoCamposInstrumento {
    private final Map<CampoInstrumento, DefinicaoCampo> definicoes;

    public CatalogoCamposInstrumento() {
        EnumMap<CampoInstrumento, DefinicaoCampo> catalogo =
                new EnumMap<>(CampoInstrumento.class);
        catalogo.put(OBJETO, definicao(
                InstrumentoContratual::getObjeto,
                InstrumentoEstadoInicial::getObjeto,
                InstrumentoContratual::setObjeto,
                valor -> validarTextoObrigatorio(OBJETO, valor, 1000)));
        catalogo.put(DESCRICAO, definicao(
                InstrumentoContratual::getDescricao,
                InstrumentoEstadoInicial::getDescricao,
                InstrumentoContratual::setDescricao,
                valor -> validarTextoOpcional(DESCRICAO, valor, 2000)));
        catalogo.put(NATUREZA, definicao(
                InstrumentoContratual::getNatureza,
                InstrumentoEstadoInicial::getNatureza,
                InstrumentoContratual::setNatureza,
                valor -> validarTextoObrigatorio(NATUREZA, valor, 150)));
        catalogo.put(COORDENADOR, definicao(
                InstrumentoContratual::getCoordenador,
                InstrumentoEstadoInicial::getCoordenador,
                InstrumentoContratual::setCoordenador,
                valor -> validarTextoObrigatorio(COORDENADOR, valor, 150)));
        catalogo.put(PARTICIPES, definicao(
                InstrumentoContratual::getParticipes,
                InstrumentoEstadoInicial::getParticipes,
                InstrumentoContratual::setParticipes,
                valor -> validarTextoObrigatorio(PARTICIPES, valor, 2000)));
        catalogo.put(VALOR_ATUAL, definicao(
                instrumento -> instrumento.getValorAtual().toPlainString(),
                estado -> estado.getValorAtual().toPlainString(),
                (instrumento, valor) -> instrumento.setValorAtual(new BigDecimal(valor)),
                this::validarValorMonetario));
        catalogo.put(VIGENCIA_CONTRATUAL_FINAL, definicao(
                instrumento -> instrumento.getVigenciaContratualFinal().toString(),
                estado -> estado.getVigenciaContratualFinal().toString(),
                (instrumento, valor) ->
                        instrumento.setVigenciaContratualFinal(LocalDate.parse(valor)),
                valor -> validarData(VIGENCIA_CONTRATUAL_FINAL, valor, false)));
        catalogo.put(VIGENCIA_TED_FINAL, definicao(
                instrumento -> texto(instrumento.getVigenciaTedFinal()),
                estado -> texto(estado.getVigenciaTedFinal()),
                (instrumento, valor) -> instrumento.setVigenciaTedFinal(
                        valor == null || valor.isBlank() ? null : LocalDate.parse(valor)),
                valor -> validarData(VIGENCIA_TED_FINAL, valor, true)));

        if (catalogo.size() != CampoInstrumento.values().length) {
            throw new IllegalStateException("O catálogo não cobre todos os campos do instrumento.");
        }
        definicoes = Collections.unmodifiableMap(catalogo);
    }

    public String valorAtual(InstrumentoContratual instrumento, CampoInstrumento campo) {
        return definicao(campo).leitorAtual().apply(Objects.requireNonNull(instrumento));
    }

    public String valorInicial(InstrumentoEstadoInicial estado, CampoInstrumento campo) {
        return definicao(campo).leitorInicial().apply(Objects.requireNonNull(estado));
    }

    public void validarNovoValor(CampoInstrumento campo, String valor) {
        definicao(campo).validador().validar(valor);
    }

    public void aplicar(InstrumentoContratual instrumento, CampoInstrumento campo, String valor) {
        definicao(campo).aplicador().accept(Objects.requireNonNull(instrumento), valor);
    }

    private DefinicaoCampo definicao(CampoInstrumento campo) {
        return Objects.requireNonNull(definicoes.get(Objects.requireNonNull(campo)));
    }

    private DefinicaoCampo definicao(
            Function<InstrumentoContratual, String> leitorAtual,
            Function<InstrumentoEstadoInicial, String> leitorInicial,
            BiConsumer<InstrumentoContratual, String> aplicador,
            ValidadorValor validador) {
        return new DefinicaoCampo(leitorAtual, leitorInicial, aplicador, validador);
    }

    private void validarTextoObrigatorio(CampoInstrumento campo, String valor, int limite) {
        if (valor == null || valor.isBlank() || valor.length() > limite) {
            throw new DomainException(
                    "O novo valor de %s deve ser preenchido e ter no máximo %d caracteres."
                            .formatted(campo, limite));
        }
    }

    private void validarTextoOpcional(CampoInstrumento campo, String valor, int limite) {
        if (valor != null && valor.length() > limite) {
            throw new DomainException(
                    "O novo valor de %s deve ter no máximo %d caracteres."
                            .formatted(campo, limite));
        }
    }

    private void validarValorMonetario(String valor) {
        try {
            BigDecimal numero = new BigDecimal(valor);
            if (numero.signum() < 0 || numero.scale() > 2 || numero.precision() > 19) {
                throw new NumberFormatException();
            }
        } catch (NullPointerException | NumberFormatException e) {
            throw new DomainException(
                    "O novo valor de VALOR_ATUAL deve ser um número decimal não negativo.");
        }
    }

    private void validarData(CampoInstrumento campo, String valor, boolean opcional) {
        if (opcional && (valor == null || valor.isBlank())) return;
        try {
            LocalDate.parse(valor);
        } catch (RuntimeException e) {
            throw new DomainException(
                    "O novo valor de %s deve ser uma data no formato AAAA-MM-DD."
                            .formatted(campo));
        }
    }

    private String texto(LocalDate data) {
        return data == null ? null : data.toString();
    }

    private record DefinicaoCampo(
            Function<InstrumentoContratual, String> leitorAtual,
            Function<InstrumentoEstadoInicial, String> leitorInicial,
            BiConsumer<InstrumentoContratual, String> aplicador,
            ValidadorValor validador) {}

    @FunctionalInterface
    private interface ValidadorValor {
        void validar(String valor);
    }
}
