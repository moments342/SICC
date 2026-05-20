package com.moments.sicc.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "documentos_anexos")
public class DocumentoAnexo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    private ProcessoContratual processo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_upload_id")
    private Usuario usuarioUpload;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(length = 120)
    private String categoriaDocumento;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false, length = 255)
    private String nomeArquivoOriginal;

    @Column(nullable = false, length = 255)
    private String caminhoArmazenamento;

    @Column(length = 100)
    private String tipoMime;

    private Long tamanhoBytes;

    @Column(nullable = false)
    private boolean restrito = true;

    @Column(nullable = false)
    private Integer versaoAtual = 1;

    @OneToMany(mappedBy = "documento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentoVersao> versoes = new ArrayList<>();
}
