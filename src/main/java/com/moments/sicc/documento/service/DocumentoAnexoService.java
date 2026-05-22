package com.moments.sicc.documento.service;

import com.moments.sicc.documento.domain.DocumentoAnexo;
import com.moments.sicc.documento.repository.DocumentoAnexoRepository;
import com.moments.sicc.processo.domain.Processo;
import com.moments.sicc.processo.repository.ProcessoRepository;
import com.moments.sicc.shared.exception.NotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentoAnexoService {

    private final DocumentoAnexoRepository documentoAnexoRepository;
    private final ProcessoRepository processoRepository;

    @Transactional
    public DocumentoAnexo anexar(Long processoId, DocumentoAnexo documento) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new NotFoundException("Processo nao encontrado."));
        documento.setProcesso(processo);
        documento.anexar();
        return documentoAnexoRepository.save(documento);
    }

    @Transactional
    public DocumentoAnexo atualizarArquivo(Long documentoId, String nomeArquivo, String tipoArquivo, String caminhoArquivo) {
        DocumentoAnexo documento = buscarPorId(documentoId);
        documento.atualizarArquivo(nomeArquivo, tipoArquivo, caminhoArquivo);
        return documentoAnexoRepository.save(documento);
    }

    @Transactional
    public DocumentoAnexo removerLogicamente(Long documentoId) {
        DocumentoAnexo documento = buscarPorId(documentoId);
        documento.removerLogicamente();
        return documentoAnexoRepository.save(documento);
    }

    private DocumentoAnexo buscarPorId(Long documentoId) {
        return documentoAnexoRepository.findById(documentoId)
                .orElseThrow(() -> new NotFoundException("Documento nao encontrado."));
    }
}
