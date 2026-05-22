package com.moments.sicc.tramitacao.service;

import com.moments.sicc.processo.domain.Processo;
import com.moments.sicc.processo.repository.ProcessoRepository;
import com.moments.sicc.shared.exception.NotFoundException;
import com.moments.sicc.tramitacao.domain.Tramitacao;
import com.moments.sicc.tramitacao.repository.TramitacaoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TramitacaoService {

    private final TramitacaoRepository tramitacaoRepository;
    private final ProcessoRepository processoRepository;

    @Transactional
    public Tramitacao registrar(Long processoId, Tramitacao tramitacao) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new NotFoundException("Processo nao encontrado."));
        tramitacao.aplicarAoProcesso(processo);
        tramitacao.registrar();
        processoRepository.save(processo);
        return tramitacaoRepository.save(tramitacao);
    }
}
