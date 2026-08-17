package com.moments.sicc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoNotificacao;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.Notificacao;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.NotificacaoRepository;
import com.moments.sicc.repository.UsuarioInternoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class VigenciaSchedulerTest {
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 1);

    @Test
    void avaliaMarcosIndependentementeAtualizaStatusEVinculaAlertasSemDuplicar() {
        InstrumentoContratualRepository instrumentos = mock(InstrumentoContratualRepository.class);
        UsuarioInternoRepository usuarios = mock(UsuarioInternoRepository.class);
        NotificacaoRepository notificacoes = mock(NotificacaoRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        Clock relogio = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);
        VigenciaScheduler scheduler = new VigenciaScheduler(
                instrumentos, usuarios, notificacoes, auditoria, new RegrasDeVigencia(relogio));

        UsuarioInterno destinatario = usuario(7L);
        InstrumentoContratual aCentoEVinte = instrumento(
                10L, 110L, "CV-120", HOJE.plusDays(120), HOJE.plusDays(120));
        InstrumentoContratual aCentoEVinteEUm = instrumento(
                11L, 111L, "CV-121", HOJE.plusDays(121), HOJE.plusDays(121));
        InstrumentoContratual noDiaFinal = instrumento(
                12L, 112L, "CV-000", HOJE, HOJE);
        InstrumentoContratual contratoVencido = instrumento(
                13L, 113L, "CV-VENCIDO", HOJE.minusDays(1), null);
        InstrumentoContratual tedVencido = instrumento(
                14L, 114L, "CV-TED-VENCIDO", HOJE.plusDays(121), HOJE.minusDays(1));
        when(instrumentos.findAllByProcessoAtivoTrue()).thenReturn(List.of(
                aCentoEVinte, aCentoEVinteEUm, noDiaFinal, contratoVencido, tedVencido));
        when(usuarios.findByAtivoTrue()).thenReturn(List.of(destinatario));

        var chavesPersistidas = new HashSet<String>();
        var alertasPersistidos = new ArrayList<Notificacao>();
        when(notificacoes.existsByChaveIdempotencia(any()))
                .thenAnswer(invocacao -> chavesPersistidas.contains(invocacao.getArgument(0)));
        when(notificacoes.save(any())).thenAnswer(invocacao -> {
            Notificacao notificacao = invocacao.getArgument(0);
            chavesPersistidas.add(notificacao.getChaveIdempotencia());
            alertasPersistidos.add(notificacao);
            return notificacao;
        });

        scheduler.avaliar();
        scheduler.avaliar();

        assertThat(alertasPersistidos)
                .extracting(Notificacao::getTipo)
                .containsExactlyInAnyOrder(
                        TipoNotificacao.ALERTA_VIGENCIA_CONTRATUAL,
                        TipoNotificacao.ALERTA_VIGENCIA_TED);
        assertThat(alertasPersistidos)
                .allSatisfy(alerta -> {
                    assertThat(alerta.getProcesso()).isSameAs(aCentoEVinte.getProcesso());
                    assertThat(alerta.getDestinatario()).isSameAs(destinatario);
                    assertThat(alerta.getMensagem()).contains("120 dias");
                });
        assertThat(alertasPersistidos)
                .extracting(Notificacao::getMensagem)
                .anySatisfy(mensagem -> assertThat(mensagem).contains("contratual"))
                .anySatisfy(mensagem -> assertThat(mensagem).contains("TED"));

        assertThat(aCentoEVinte.getProcesso().getStatus()).isEqualTo(StatusProcesso.EM_VIGENCIA);
        assertThat(aCentoEVinteEUm.getProcesso().getStatus()).isEqualTo(StatusProcesso.EM_VIGENCIA);
        assertThat(noDiaFinal.getProcesso().getStatus()).isEqualTo(StatusProcesso.EM_VIGENCIA);
        assertThat(contratoVencido.getProcesso().getStatus()).isEqualTo(StatusProcesso.CONCLUIDO);
        assertThat(tedVencido.getProcesso().getStatus()).isEqualTo(StatusProcesso.EM_VIGENCIA);
        verify(auditoria).registrar(
                null, "ALTERAR_STATUS_AUTOMATICO", "PROCESSO_ADMINISTRATIVO", 113L,
                true, "EM_VIGENCIA -> CONCLUIDO", "SISTEMA");
    }

    private InstrumentoContratual instrumento(
            Long instrumentoId,
            Long processoId,
            String numero,
            LocalDate vigenciaContratual,
            LocalDate vigenciaTed) {
        ProcessoAdministrativo processo = new ProcessoAdministrativo();
        processo.setId(processoId);
        processo.setNumero("PROC-" + processoId);
        processo.setStatus(StatusProcesso.EM_VIGENCIA);
        InstrumentoContratual instrumento = new InstrumentoContratual();
        instrumento.setId(instrumentoId);
        instrumento.setProcesso(processo);
        instrumento.setNumero(numero);
        instrumento.setVigenciaContratualFinal(vigenciaContratual);
        instrumento.setVigenciaTedFinal(vigenciaTed);
        return instrumento;
    }

    private UsuarioInterno usuario(Long id) {
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setId(id);
        usuario.setNome("Operador DIPAC");
        usuario.setAtivo(true);
        return usuario;
    }
}
