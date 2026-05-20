package com.moments.sicc.domain.entity;

import com.moments.sicc.domain.enums.TipoResponsabilidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "responsaveis_processo")
public class ResponsavelProcesso extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    private ProcessoContratual processo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etapa_fluxo_id")
    private EtapaFluxo etapaFluxo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoResponsabilidade tipoResponsabilidade;

    @Column(nullable = false)
    private boolean principal = false;

    private LocalDate dataInicio = LocalDate.now();

    private LocalDate dataFim;

    @Column(length = 300)
    private String observacao;
}
