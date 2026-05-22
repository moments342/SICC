package com.moments.sicc.documento.domain;

import com.moments.sicc.processo.domain.Processo;
import com.moments.sicc.shared.domain.BaseEntity;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.usuario.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "documentos_anexos")
public class DocumentoAnexo extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String nomeArquivo;

    @Column(nullable = false, length = 120)
    private String tipoArquivo;

    @Column(nullable = false, length = 500)
    private String caminhoArquivo;

    @Column(length = 1000)
    private String descricao;

    @Column(nullable = false)
    private Integer versao = 1;

    @Column(nullable = false)
    private LocalDateTime dataUpload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enviado_por_id")
    private Usuario enviadoPor;

    @Column(nullable = false)
    private boolean ativo = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    private Processo processo;

    public void anexar() {
        validarArquivo();
        ativo = true;
        if (versao == null || versao < 1) {
            versao = 1;
        }
        if (dataUpload == null) {
            dataUpload = LocalDateTime.now();
        }
    }

    public void atualizarArquivo(String novoNomeArquivo, String novoTipoArquivo, String novoCaminhoArquivo) {
        validarDadosArquivo(novoNomeArquivo, novoTipoArquivo, novoCaminhoArquivo);
        nomeArquivo = novoNomeArquivo;
        tipoArquivo = novoTipoArquivo;
        caminhoArquivo = novoCaminhoArquivo;
        incrementarVersao();
        dataUpload = LocalDateTime.now();
    }

    public void incrementarVersao() {
        if (versao == null || versao < 1) {
            versao = 1;
            return;
        }
        versao++;
    }

    public void removerLogicamente() {
        ativo = false;
    }

    private void validarArquivo() {
        validarDadosArquivo(nomeArquivo, tipoArquivo, caminhoArquivo);
    }

    private void validarDadosArquivo(String nomeArquivo, String tipoArquivo, String caminhoArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            throw new DomainException("Nome do arquivo e obrigatorio.");
        }
        if (tipoArquivo == null || tipoArquivo.isBlank()) {
            throw new DomainException("Tipo do arquivo e obrigatorio.");
        }
        if (caminhoArquivo == null || caminhoArquivo.isBlank()) {
            throw new DomainException("Caminho do arquivo e obrigatorio.");
        }
    }
}
