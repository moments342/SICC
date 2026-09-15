package com.moments.sicc.service;

import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.Notificacao;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoNotificacao;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.NotificacaoRepository;
import com.moments.sicc.repository.UsuarioInternoRepository;
import com.moments.sicc.service.RegrasDeVigencia.ReferenciaDeVigencia;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final RegrasDeVigencia regrasDeVigencia;
    private final Clock clock;

    @Scheduled(cron = "0 5 1 * * *")
    @Transactional
    public void avaliar() {
        LocalDateTime criadaEm = LocalDateTime.now(clock);
        ReferenciaDeVigencia referenciaDeVigencia =
                regrasDeVigencia.referenciaEm(criadaEm.toLocalDate());
        for (InstrumentoContratual instrumento : instrumentos.findAllByProcessoAtivoTrue()) {
            StatusProcesso anterior = instrumento.getProcesso().getStatus();
            StatusProcesso atual = referenciaDeVigencia.status(
                    instrumento.getVigenciaContratualFinal());
            if (anterior != atual) {
                instrumento.getProcesso().setStatus(atual);
                auditoria.registrar(null, "ALTERAR_STATUS_AUTOMATICO", "PROCESSO_ADMINISTRATIVO",
                        instrumento.getProcesso().getId(), true, anterior + " -> " + atual, "SISTEMA");
            }
            alertar(
                    instrumento,
                    TipoNotificacao.ALERTA_VIGENCIA_CONTRATUAL,
                    instrumento.getVigenciaContratualFinal(),
                    referenciaDeVigencia,
                    criadaEm);
            alertar(
                    instrumento,
                    TipoNotificacao.ALERTA_VIGENCIA_TED,
                    instrumento.getVigenciaTedFinal(),
                    referenciaDeVigencia,
                    criadaEm);
        }
    }

    private void alertar(
            InstrumentoContratual instrumento,
            TipoNotificacao tipo,
            LocalDate vencimento,
            ReferenciaDeVigencia referenciaDeVigencia,
            LocalDateTime criadaEm) {
        if (!referenciaDeVigencia.estaNoMarcoDeAlerta(vencimento)) return;
        var responsavel = instrumento.getProcesso().getResponsavel();
        List<UsuarioInterno> destinatarios = responsavel != null && responsavel.isAtivo()
                ? List.of(responsavel) : usuarios.findByAtivoTrue();
        destinatarios.forEach(usuario -> {
            String descricao = switch (tipo) {
                case ALERTA_VIGENCIA_CONTRATUAL -> "contratual";
                case ALERTA_VIGENCIA_TED -> "do TED";
                default -> throw new IllegalArgumentException(
                        "Tipo de Notificação Interna incompatível com alerta de vigência.");
            };
            String chaveIdempotencia = chaveIdempotenciaDoAlerta(
                    instrumento, tipo, vencimento, usuario);
            if (notificacoes.existsByChaveIdempotencia(chaveIdempotencia)) return;
            Notificacao notificacao = new Notificacao();
            notificacao.setDestinatario(usuario);
            notificacao.setProcesso(instrumento.getProcesso());
            notificacao.setTipo(tipo);
            notificacao.setChaveIdempotencia(chaveIdempotencia);
            notificacao.setMensagem("A vigência " + descricao + " do instrumento "
                    + instrumento.getNumero() + " vence em 120 dias.");
            notificacao.setCriadaEm(criadaEm);
            notificacoes.save(notificacao);
        });
    }

    private String chaveIdempotenciaDoAlerta(
            InstrumentoContratual instrumento,
            TipoNotificacao tipo,
            LocalDate vencimento,
            UsuarioInterno destinatario) {
        return "VIGENCIA:" + instrumento.getProcesso().getId() + ":" + tipo
                + ":" + vencimento + ":" + destinatario.getId();
    }
}
