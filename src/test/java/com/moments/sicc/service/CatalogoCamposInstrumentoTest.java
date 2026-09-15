package com.moments.sicc.service;

import static com.moments.sicc.domain.Enums.CampoInstrumento.COORDENADOR;
import static com.moments.sicc.domain.Enums.CampoInstrumento.DESCRICAO;
import static com.moments.sicc.domain.Enums.CampoInstrumento.NATUREZA;
import static com.moments.sicc.domain.Enums.CampoInstrumento.OBJETO;
import static com.moments.sicc.domain.Enums.CampoInstrumento.PARTICIPES;
import static com.moments.sicc.domain.Enums.CampoInstrumento.VALOR_ATUAL;
import static com.moments.sicc.domain.Enums.CampoInstrumento.VIGENCIA_CONTRATUAL_FINAL;
import static com.moments.sicc.domain.Enums.CampoInstrumento.VIGENCIA_TED_FINAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moments.sicc.domain.Enums.CampoInstrumento;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.InstrumentoEstadoInicial;
import com.moments.sicc.shared.exception.DomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogoCamposInstrumentoTest {

    private final CatalogoCamposInstrumento catalogo = new CatalogoCamposInstrumento();

    @Test
    void cobreTodoOCatalogoAoLerOEstadoInicialEAplicarNovosValores() {
        InstrumentoContratual instrumento = instrumentoCompleto();
        InstrumentoEstadoInicial estadoInicial = InstrumentoEstadoInicial.copiarDe(instrumento);

        assertThat(CampoInstrumento.values()).allSatisfy(campo ->
                assertThat(catalogo.valorInicial(estadoInicial, campo))
                        .isEqualTo(catalogo.valorAtual(instrumento, campo)));

        Map<CampoInstrumento, String> novosValores = new EnumMap<>(CampoInstrumento.class);
        novosValores.put(OBJETO, "Novo objeto");
        novosValores.put(DESCRICAO, null);
        novosValores.put(NATUREZA, "Nova natureza");
        novosValores.put(COORDENADOR, "Nova coordenação");
        novosValores.put(PARTICIPES, "UFGD\nParceiro");
        novosValores.put(VALOR_ATUAL, "123.45");
        novosValores.put(VIGENCIA_CONTRATUAL_FINAL, "2028-12-31");
        novosValores.put(VIGENCIA_TED_FINAL, "");

        novosValores.forEach((campo, valor) -> {
            catalogo.validarNovoValor(campo, valor);
            catalogo.aplicar(instrumento, campo, valor);
        });

        assertThat(catalogo.valorAtual(instrumento, OBJETO)).isEqualTo("Novo objeto");
        assertThat(catalogo.valorAtual(instrumento, DESCRICAO)).isNull();
        assertThat(catalogo.valorAtual(instrumento, VALOR_ATUAL)).isEqualTo("123.45");
        assertThat(catalogo.valorAtual(instrumento, VIGENCIA_CONTRATUAL_FINAL))
                .isEqualTo("2028-12-31");
        assertThat(catalogo.valorAtual(instrumento, VIGENCIA_TED_FINAL)).isNull();
    }

    @Test
    void rejeitaValoresQueViolamOContratoDoCampo() {
        assertThatThrownBy(() -> catalogo.validarNovoValor(OBJETO, " "))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("OBJETO");
        assertThatThrownBy(() -> catalogo.validarNovoValor(VALOR_ATUAL, "-1"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("decimal não negativo");
        assertThatThrownBy(() -> catalogo.validarNovoValor(
                VIGENCIA_CONTRATUAL_FINAL, "31/12/2028"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("AAAA-MM-DD");
    }

    private InstrumentoContratual instrumentoCompleto() {
        InstrumentoContratual instrumento = new InstrumentoContratual();
        instrumento.setObjeto("Objeto original");
        instrumento.setDescricao("Descrição original");
        instrumento.setNatureza("Natureza original");
        instrumento.setCoordenador("Coordenação original");
        instrumento.setParticipes("UFGD");
        instrumento.setValorAtual(new BigDecimal("100.00"));
        instrumento.setVigenciaContratualFinal(LocalDate.of(2027, 12, 31));
        instrumento.setVigenciaTedFinal(LocalDate.of(2027, 6, 30));
        return instrumento;
    }
}
