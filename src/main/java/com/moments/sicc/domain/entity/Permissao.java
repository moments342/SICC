package com.moments.sicc.domain.entity;

import com.moments.sicc.domain.enums.AcaoPermissao;
import com.moments.sicc.domain.enums.ModuloSistema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "permissoes",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"usuario_id", "modulo", "acao"})
        }
)
public class Permissao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ModuloSistema modulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AcaoPermissao acao;

    @Column(nullable = false)
    private boolean concedida = true;
}
