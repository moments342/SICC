package com.moments.sicc.documento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moments.sicc.documento.domain.DocumentoAnexo;
import com.moments.sicc.documento.repository.DocumentoAnexoRepository;
import com.moments.sicc.processo.domain.Processo;
import com.moments.sicc.processo.repository.ProcessoRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentoAnexoServiceTest {

    @Mock
    private DocumentoAnexoRepository documentoAnexoRepository;

    @Mock
    private ProcessoRepository processoRepository;

    @InjectMocks
    private DocumentoAnexoService documentoAnexoService;

    @Test
    void anexarAssociaProcessoESalvaDocumento() {
        Processo processo = new Processo();
        DocumentoAnexo documento = documentoValido();
        when(processoRepository.findById(1L)).thenReturn(Optional.of(processo));
        when(documentoAnexoRepository.save(documento)).thenReturn(documento);

        DocumentoAnexo salvo = documentoAnexoService.anexar(1L, documento);

        assertThat(salvo.getProcesso()).isSameAs(processo);
        assertThat(salvo.getDataUpload()).isNotNull();
        verify(documentoAnexoRepository).save(documento);
    }

    @Test
    void atualizarArquivoIncrementaVersao() {
        DocumentoAnexo documento = documentoValido();
        documento.anexar();
        when(documentoAnexoRepository.findById(1L)).thenReturn(Optional.of(documento));
        when(documentoAnexoRepository.save(documento)).thenReturn(documento);

        DocumentoAnexo atualizado = documentoAnexoService.atualizarArquivo(
                1L, "contrato-v2.pdf", "application/pdf", "/docs/contrato-v2.pdf");

        assertThat(atualizado.getVersao()).isEqualTo(2);
    }

    @Test
    void removerLogicamenteDesativaDocumento() {
        DocumentoAnexo documento = documentoValido();
        when(documentoAnexoRepository.findById(1L)).thenReturn(Optional.of(documento));
        when(documentoAnexoRepository.save(documento)).thenReturn(documento);

        DocumentoAnexo removido = documentoAnexoService.removerLogicamente(1L);

        assertThat(removido.isAtivo()).isFalse();
    }

    private DocumentoAnexo documentoValido() {
        DocumentoAnexo documento = new DocumentoAnexo();
        documento.setNomeArquivo("contrato.pdf");
        documento.setTipoArquivo("application/pdf");
        documento.setCaminhoArquivo("/docs/contrato.pdf");
        return documento;
    }
}
