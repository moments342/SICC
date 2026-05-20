package com.moments.sicc.domain.entity;

import com.moments.sicc.domain.enums.StatusProcesso;
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
        name = "etapas_fluxo",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"fluxo_id", "ordem_exibicao"})
        }
)
public class EtapaFluxo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fluxo_id", nullable = false)
    private FluxoTramitacao fluxo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(name = "ordem_exibicao", nullable = false)
    private Integer ordemExibicao;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false)
    private boolean obrigatoria = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "setor_responsavel_id")
    private Setor setorResponsavel;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private StatusProcesso statusResultante;

    @Column(nullable = false)
    private boolean notificarCoordenador = false;

    @Column
    private Integer prazoPadraoDias;
}
