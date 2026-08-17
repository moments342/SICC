package com.moments.sicc.domain;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "movimentacoes")
public class Movimentacao extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContextoTramitacao contextoTipo;
    @Column(nullable = false)
    private Long contextoId;
    @Column(nullable = false)
    private LocalDate dataMovimentacao;
    @Column(nullable = false)
    private int sequenciaDiaria;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setor_destino_id", nullable = false)
    private Setor setorDestino;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private UsuarioInterno autor;
    @Column(length = 1000)
    private String observacao;
    @Column(nullable = false, updatable = false)
    private LocalDateTime inseridoEm = LocalDateTime.now();
}
