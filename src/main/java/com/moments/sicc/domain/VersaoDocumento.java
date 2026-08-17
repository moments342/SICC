package com.moments.sicc.domain;

import com.moments.sicc.shared.domain.BaseEntity;
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
@Table(name = "versoes_documento")
public class VersaoDocumento extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;
    @Column(nullable = false)
    private int versao;
    @Column(nullable = false, length = 255)
    private String nomeArquivo;
    @Column(nullable = false, length = 120)
    private String tipoMime;
    @Column(nullable = false)
    private long tamanho;
    @Column(nullable = false, length = 64)
    private String checksumSha256;
    @Column(nullable = false, unique = true, length = 500)
    private String chaveArmazenamento;
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private UsuarioInterno criadoPor;
}
