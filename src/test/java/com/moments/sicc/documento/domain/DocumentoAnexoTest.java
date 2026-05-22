package com.moments.sicc.documento.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moments.sicc.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

class DocumentoAnexoTest {

    @Test
    void anexarPreencheDataUploadEAtivaDocumento() {
        DocumentoAnexo documento = documentoValido();

        documento.anexar();

        assertThat(documento.isAtivo()).isTrue();
        assertThat(documento.getVersao()).isEqualTo(1);
        assertThat(documento.getDataUpload()).isNotNull();
    }

    @Test
    void atualizarArquivoIncrementaVersao() {
        DocumentoAnexo documento = documentoValido();
        documento.anexar();

        documento.atualizarArquivo("contrato-v2.pdf", "application/pdf", "/docs/contrato-v2.pdf");

        assertThat(documento.getNomeArquivo()).isEqualTo("contrato-v2.pdf");
        assertThat(documento.getVersao()).isEqualTo(2);
    }

    @Test
    void atualizarArquivoInvalidoNaoAlteraDadosAtuais() {
        DocumentoAnexo documento = documentoValido();
        documento.anexar();

        assertThatThrownBy(() -> documento.atualizarArquivo("", "application/pdf", "/docs/contrato-v2.pdf"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Nome do arquivo");

        assertThat(documento.getNomeArquivo()).isEqualTo("contrato.pdf");
        assertThat(documento.getTipoArquivo()).isEqualTo("application/pdf");
        assertThat(documento.getCaminhoArquivo()).isEqualTo("/docs/contrato.pdf");
        assertThat(documento.getVersao()).isEqualTo(1);
    }

    @Test
    void removerLogicamenteDesativaDocumento() {
        DocumentoAnexo documento = documentoValido();

        documento.removerLogicamente();

        assertThat(documento.isAtivo()).isFalse();
    }

    @Test
    void anexarExigeNomeArquivo() {
        DocumentoAnexo documento = documentoValido();
        documento.setNomeArquivo("");

        assertThatThrownBy(documento::anexar)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Nome do arquivo");
    }

    static DocumentoAnexo documentoValido() {
        DocumentoAnexo documento = new DocumentoAnexo();
        documento.setNomeArquivo("contrato.pdf");
        documento.setTipoArquivo("application/pdf");
        documento.setCaminhoArquivo("/docs/contrato.pdf");
        documento.setDescricao("Contrato assinado");
        return documento;
    }
}
