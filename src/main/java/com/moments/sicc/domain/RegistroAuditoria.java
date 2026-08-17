package com.moments.sicc.domain;

import com.moments.sicc.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Immutable
@Table(name = "registros_auditoria")
public class RegistroAuditoria extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioInterno usuario;
    @Column(nullable = false, length = 80)
    private String acao;
    @Column(nullable = false, length = 80)
    private String entidade;
    private Long entidadeId;
    @Column(nullable = false)
    private boolean sucesso;
    @Column(length = 2000)
    private String detalhes;
    @Column(length = 80)
    private String ipOrigem;
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public RegistroAuditoria(
            UsuarioInterno usuario,
            String acao,
            String entidade,
            Long entidadeId,
            boolean sucesso,
            String detalhes,
            String ipOrigem) {
        this.usuario = usuario;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.sucesso = sucesso;
        this.detalhes = detalhes;
        this.ipOrigem = ipOrigem;
    }
}
