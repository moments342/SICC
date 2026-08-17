package com.moments.sicc.service;

import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.Notificacao;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.NotificacaoRepository;
import com.moments.sicc.repository.UsuarioInternoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sicc.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class VigenciaScheduler {
    private final InstrumentoContratualRepository instrumentos;
    private final UsuarioInternoRepository usuarios;
    private final NotificacaoRepository notificacoes;
    private final AuditoriaService auditoria;
    private final Clock clock;

    @Scheduled(cron = "0 5 1 * * *")
    @Transactional
    public void avaliar() {
        LocalDate hoje = LocalDate.now(clock);
        for (InstrumentoContratual instrumento : instrumentos.findAllByProcessoAtivoTrue()) {
            StatusProcesso anterior = instrumento.getProcesso().getStatus();
            StatusProcesso atual = instrumento.getVigenciaContratualFinal().isBefore(hoje)
                    ? StatusProcesso.CONCLUIDO : StatusProcesso.EM_VIGENCIA;
            if (anterior != atual) {
                instrumento.getProcesso().setStatus(atual);
                auditoria.registrar(null, "ALTERAR_STATUS_AUTOMATICO", "PROCESSO_ADMINISTRATIVO",
                        instrumento.getProcesso().getId(), true, anterior + " -> " + atual, "SISTEMA");
            }
            alertar(instrumento, "CONTRATUAL", instrumento.getVigenciaContratualFinal(), hoje);
            alertar(instrumento, "TED", instrumento.getVigenciaTedFinal(), hoje);
        }
    }

    private void alertar(InstrumentoContratual instrumento, String tipo, LocalDate vencimento, LocalDate hoje) {
        if (vencimento == null || ChronoUnit.DAYS.between(hoje, vencimento) != 120) return;
        var destinatarios = instrumento.getProcesso().getResponsavel() == null
                ? usuarios.findByAtivoTrue() : java.util.List.of(instrumento.getProcesso().getResponsavel());
        destinatarios.forEach(usuario -> {
            String key = "VIGENCIA:" + instrumento.getId() + ":" + tipo + ":" + vencimento + ":" + usuario.getId();
            if (notificacoes.existsByChaveIdempotencia(key)) return;
            Notificacao notificacao = new Notificacao();
            notificacao.setDestinatario(usuario);
            notificacao.setTipo("ALERTA_VIGENCIA_" + tipo);
            notificacao.setChaveIdempotencia(key);
            notificacao.setMensagem("A vigência " + tipo.toLowerCase() + " do instrumento "
                    + instrumento.getNumero() + " vence em 120 dias.");
            notificacoes.save(notificacao);
        });
    }
}
