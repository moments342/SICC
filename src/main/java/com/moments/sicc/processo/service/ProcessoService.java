package com.moments.sicc.processo.service;

import com.moments.sicc.processo.domain.Processo;
import com.moments.sicc.processo.repository.ProcessoRepository;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProcessoService {

    private final ProcessoRepository processoRepository;

    @Transactional
    public Processo cadastrar(Processo processo) {
        processo.cadastrar();
        if (processoRepository.existsByNumeroProcesso(processo.getNumeroProcesso())) {
            throw new DomainException("Ja existe processo cadastrado com este numero.");
        }
        return processoRepository.save(processo);
    }

    @Transactional
    public Processo editarDados(Long processoId, String objeto, String descricao) {
        Processo processo = buscarPorId(processoId);
        processo.editarDados(objeto, descricao);
        return processoRepository.save(processo);
    }

    public Processo buscarPorId(Long processoId) {
        return processoRepository.findById(processoId)
                .orElseThrow(() -> new NotFoundException("Processo nao encontrado."));
    }
}
