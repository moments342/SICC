package com.moments.sicc.service;

import static com.moments.sicc.domain.Enums.FormatoRelatorio.CSV;
import static com.moments.sicc.domain.Enums.StatusProcesso.EM_VIGENCIA;
import static com.moments.sicc.domain.Enums.TipoInstrumento.CONVENIO;
import static com.moments.sicc.domain.Enums.TipoRelatorio.CONSOLIDADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.api.ApiDtos.GerarRelatorioRequest;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.MovimentacaoRepository;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.repository.RelatorioGeradoRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RelatorioDashboardReferenciaTemporalTest {
    private ProcessoAdministrativoRepository processos;
    private InstrumentoContratualRepository instrumentos;
    private MovimentacaoRepository movimentacoes;
    private ArmazenamentoTransacional storage;
    private AuditoriaService auditoria;

    @BeforeEach
    void prepararDependencias() {
        processos = mock(ProcessoAdministrativoRepository.class);
        instrumentos = mock(InstrumentoContratualRepository.class);
        movimentacoes = mock(MovimentacaoRepository.class);
        storage = mock(ArmazenamentoTransacional.class);
        auditoria = mock(AuditoriaService.class);
    }

    @Test
    void geracaoUsaUmaUnicaDataParaEmissaoFiltrosEConteudo() {
        RelogioQueCruzaMeiaNoite relogio = new RelogioQueCruzaMeiaNoite();
        RelatorioDashboardService service = service(relogio);
        ProcessoAdministrativo processo = processoComInstrumentoNoLimite();
        when(processos.findByAtivoTrue()).thenReturn(List.of(processo));
        when(instrumentos.findByProcessoId(processo.getId()))
                .thenReturn(Optional.of(processo.getInstrumento()));
        when(storage.armazenar(any(byte[].class), anyString())).thenReturn("relatorios/arquivo");
        UsuarioInterno autor = autor();
        ArgumentCaptor<byte[]> conteudo = ArgumentCaptor.forClass(byte[].class);

        service.gerar(new GerarRelatorioRequest(CONSOLIDADO, CSV, Map.of()), autor, "127.0.0.1");

        org.mockito.Mockito.verify(storage).armazenar(conteudo.capture(), anyString());
        assertThat(new String(conteudo.getValue(), StandardCharsets.UTF_8))
                .contains("gerado_em;2026-08-08T23:59:59")
                .contains("PROC-001;DIPAC;EM_VIGENCIA;");
        assertThat(relogio.leituras()).isEqualTo(1);
    }

    @Test
    void dashboardUsaUmaUnicaDataParaStatusSituacoesEPeriodos() {
        RelogioQueCruzaMeiaNoite relogio = new RelogioQueCruzaMeiaNoite();
        RelatorioDashboardService service = service(relogio);
        ProcessoAdministrativo processo = processoComInstrumentoNoLimite();
        when(processos.findByAtivoTrue()).thenReturn(List.of(processo));
        when(instrumentos.findAllByProcessoAtivoTrue())
                .thenReturn(List.of(processo.getInstrumento()));
        when(movimentacoes
                .findByContextoTipoAndContextoIdOrderByDataMovimentacaoAscSequenciaDiariaAsc(
                        any(), any()))
                .thenReturn(List.of());

        var dashboard = service.dashboard(null, null, null);

        assertThat(dashboard.processosPorStatus().get(EM_VIGENCIA)).isEqualTo(1);
        assertThat(dashboard.alertasContratuais()).isEqualTo(1);
        assertThat(relogio.leituras()).isEqualTo(1);
    }

    private RelatorioDashboardService service(Clock relogio) {
        RegrasDeVigencia regras = new RegrasDeVigencia(relogio);
        GeracaoRelatorios geracao = new GeracaoRelatorios(
                instrumentos,
                mock(AlteracaoContratualRepository.class),
                movimentacoes,
                new CalculadoraPermanencia());
        return new RelatorioDashboardService(
                processos,
                instrumentos,
                movimentacoes,
                mock(RelatorioGeradoRepository.class),
                storage,
                auditoria,
                relogio,
                new CalculadoraPermanencia(),
                regras,
                new GeradorArquivoRelatorio(List.of(
                        new AdaptadorCsvRelatorio(),
                        new AdaptadorPdfRelatorio(),
                        new AdaptadorXlsxRelatorio())),
                new CatalogoFiltrosRelatorio(new ObjectMapper(), geracao),
                geracao);
    }

    private ProcessoAdministrativo processoComInstrumentoNoLimite() {
        ProcessoAdministrativo processo = new ProcessoAdministrativo();
        processo.setId(1L);
        processo.setNumero("PROC-001");
        processo.setOrigem("DIPAC");
        processo.setDataCadastro(LocalDate.of(2026, 8, 1));
        InstrumentoContratual instrumento = new InstrumentoContratual();
        instrumento.setId(2L);
        instrumento.setProcesso(processo);
        instrumento.setTipo(CONVENIO);
        instrumento.setCoordenador("Coordenação");
        instrumento.setVigenciaContratualFinal(LocalDate.of(2026, 8, 8));
        instrumento.setValorAtual(new BigDecimal("100.00"));
        instrumento.setDataFormalizacao(LocalDate.of(2026, 8, 2));
        processo.setInstrumento(instrumento);
        return processo;
    }

    private UsuarioInterno autor() {
        UsuarioInterno autor = new UsuarioInterno();
        autor.setId(3L);
        autor.setNome("Administrador");
        autor.setLogin("admin");
        return autor;
    }

    private static final class RelogioQueCruzaMeiaNoite extends Clock {
        private final AtomicInteger leituras = new AtomicInteger();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return leituras.getAndIncrement() == 0
                    ? Instant.parse("2026-08-08T23:59:59Z")
                    : Instant.parse("2026-08-09T00:00:01Z");
        }

        private int leituras() {
            return leituras.get();
        }
    }
}
