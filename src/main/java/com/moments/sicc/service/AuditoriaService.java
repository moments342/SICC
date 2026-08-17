package com.moments.sicc.service;

import com.moments.sicc.domain.RegistroAuditoria;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.RegistroAuditoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditoriaService {
    private final RegistroAuditoriaRepository registros;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(UsuarioInterno usuario, String acao, String entidade, Long entidadeId,
            boolean sucesso, String detalhes, String ip) {
        salvar(usuario, acao, entidade, entidadeId, sucesso, detalhes, ip);
    }

    @Transactional
    public void registrarNaTransacaoAtual(UsuarioInterno usuario, String acao, String entidade, Long entidadeId,
            boolean sucesso, String detalhes, String ip) {
        salvar(usuario, acao, entidade, entidadeId, sucesso, detalhes, ip);
    }

    private void salvar(UsuarioInterno usuario, String acao, String entidade, Long entidadeId,
            boolean sucesso, String detalhes, String ip) {
        RegistroAuditoria registro = new RegistroAuditoria();
        registro.setUsuario(usuario);
        registro.setAcao(acao);
        registro.setEntidade(entidade);
        registro.setEntidadeId(entidadeId);
        registro.setSucesso(sucesso);
        registro.setDetalhes(detalhes);
        registro.setIpOrigem(ip);
        registros.save(registro);
    }
}
