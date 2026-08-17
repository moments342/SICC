package com.moments.sicc.domain;

import com.moments.sicc.domain.Enums.CategoriaDocumento;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "documentos")
public class Documento extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProprietarioDocumento proprietarioTipo;
    @Column(nullable = false)
    private Long proprietarioId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CategoriaDocumento categoria;
    @Column(nullable = false, length = 255)
    private String titulo;
    @Column(nullable = false)
    private boolean ativo = true;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false, updatable = false)
    private UsuarioInterno criadoPor;
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
