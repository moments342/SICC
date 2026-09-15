package com.moments.sicc.service;

import static com.moments.sicc.domain.Enums.StatusProcesso.CONCLUIDO;
import static com.moments.sicc.domain.Enums.StatusProcesso.EM_VIGENCIA;
import static com.moments.sicc.domain.Enums.TipoInstrumento.CONVENIO;
import static org.assertj.core.api.Assertions.assertThat;

import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.ProcessoAdministrativo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ProjecoesSiccTest {
    @Test
    void projetaStatusPelaReferenciaRecebidaMesmoAoCruzarMeiaNoite() {
        LocalDate vencimento = LocalDate.of(2026, 8, 8);
        ProcessoAdministrativo processo = new ProcessoAdministrativo();
        processo.setId(1L);
        processo.setNumero("PROC-001");
        processo.setOrigem("DIPAC");
        processo.setDataCadastro(LocalDate.of(2026, 8, 1));
        InstrumentoContratual instrumento = new InstrumentoContratual();
        instrumento.setTipo(CONVENIO);
        instrumento.setCoordenador("Coordenação");
        instrumento.setVigenciaContratualFinal(vencimento);
        RegrasDeVigencia regras = new RegrasDeVigencia(Clock.fixed(
                Instant.parse("2026-08-09T00:00:01Z"), ZoneOffset.UTC));
        ProjecoesSicc projecoes = new ProjecoesSicc();

        var antesDaMeiaNoite = projecoes.processoPublico(
                processo, instrumento, regras.referenciaEm(vencimento));
        var depoisDaMeiaNoite = projecoes.processoPublico(
                processo, instrumento, regras.referenciaEm(vencimento.plusDays(1)));

        assertThat(antesDaMeiaNoite.status()).isEqualTo(EM_VIGENCIA);
        assertThat(depoisDaMeiaNoite.status()).isEqualTo(CONCLUIDO);
    }
}
