package com.moments.sicc.service;

import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.Notificacao;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.domain.Enums.TipoNotificacao;
import com.moments.sicc.repository.NotificacaoRepository;
import com.moments.sicc.repository.UsuarioInternoRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificacaoChegadaService {
    private final UsuarioInternoRepository usuarios;
    private final NotificacaoRepository notificacoes;

    @Transactional
    public void processar(ProcessoAdministrativo processo, Movimentacao movimento) {
        destinatarios(processo).stream()
                .filter(usuario -> !Objects.equals(usuario.getId(), movimento.getAutor().getId()))
                .forEach(usuario -> notificar(processo, movimento, usuario));
    }

    private List<UsuarioInterno> destinatarios(ProcessoAdministrativo processo) {
        UsuarioInterno responsavel = processo.getResponsavel();
        if (responsavel != null && responsavel.isAtivo()) {
            return List.of(responsavel);
        }
        return usuarios.findByAtivoTrue();
    }

    private void notificar(
            ProcessoAdministrativo processo,
            Movimentacao movimento,
            UsuarioInterno destinatario) {
        String chave = "CHEGADA:" + movimento.getId() + ":" + destinatario.getId();
        if (notificacoes.existsByChaveIdempotencia(chave)) {
            return;
        }
        Notificacao notificacao = new Notificacao();
        notificacao.setDestinatario(destinatario);
        notificacao.setProcesso(processo);
        notificacao.setTipo(TipoNotificacao.CHEGADA_TRAMITACAO);
        notificacao.setChaveIdempotencia(chave);
        notificacao.setMensagem("O Processo Administrativo " + processo.getNumero()
                + " chegou ao setor " + movimento.getSetorDestino().getSigla() + ".");
        notificacoes.save(notificacao);
    }
}
