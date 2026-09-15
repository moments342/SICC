package com.moments.sicc.service;

import static com.moments.sicc.domain.Enums.TipoRelatorio.CONSOLIDADO;
import static com.moments.sicc.domain.Enums.TipoRelatorio.HISTORICO_TRAMITACOES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.MovimentacaoRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogoFiltrosRelatorioTest {

    private final CatalogoFiltrosRelatorio catalogo =
            new CatalogoFiltrosRelatorio(new ObjectMapper(), new GeracaoRelatorios(
                    mock(InstrumentoContratualRepository.class),
                    mock(AlteracaoContratualRepository.class),
                    mock(MovimentacaoRepository.class),
                    new CalculadoraPermanencia()));

    @Test
    void normalizaSerializaEDesserializaFiltrosEmOrdemEstavel() {
        Map<String, String> filtros = catalogo.normalizar(CONSOLIDADO, Map.of(
                "status", " EM_VIGENCIA ",
                "origem", " DIPAC ",
                "numero", " "));

        assertThat(filtros).containsExactlyInAnyOrderEntriesOf(Map.of(
                "origem", "DIPAC",
                "status", "EM_VIGENCIA"));
        assertThat(catalogo.serializar(filtros))
                .isEqualTo("{\"origem\":\"DIPAC\",\"status\":\"EM_VIGENCIA\"}");
        assertThat(catalogo.desserializar(catalogo.serializar(filtros))).isEqualTo(filtros);
        assertThatThrownBy(() -> filtros.put("tipo", "CONVENIO"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejeitaFiltroInaplicavelEIntervaloInvertido() {
        assertThatThrownBy(() -> catalogo.normalizar(
                CONSOLIDADO, Map.of("dataInicial", "2026-08-01")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("não aplicáveis");
        assertThatThrownBy(() -> catalogo.normalizar(HISTORICO_TRAMITACOES, Map.of(
                "dataInicial", "2026-08-10",
                "dataFinal", "2026-08-01")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("dataInicial");
    }
}
