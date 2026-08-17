package com.moments.sicc.domain;

import com.moments.sicc.domain.Enums.TipoNotificacao;
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
@Table(name = "notificacoes")
public class Notificacao extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinatario_id", nullable = false)
    private UsuarioInterno destinatario;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_id")
    private ProcessoAdministrativo processo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TipoNotificacao tipo;
    @Column(nullable = false, unique = true, length = 180)
    private String chaveIdempotencia;
    @Column(nullable = false, length = 1000)
    private String mensagem;
    @Column(nullable = false)
    private boolean lida;
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();
}
