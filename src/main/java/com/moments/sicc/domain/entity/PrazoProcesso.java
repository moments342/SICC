package com.moments.sicc.domain.entity;

import com.moments.sicc.domain.enums.StatusPrazo;
import com.moments.sicc.domain.enums.TipoPrazo;
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
@Table(name = "prazos_processo")
public class PrazoProcesso extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    private ProcessoContratual processo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etapa_fluxo_id")
    private EtapaFluxo etapaFluxo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoPrazo tipoPrazo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPrazo status = StatusPrazo.NO_PRAZO;

    @Column(nullable = false, length = 200)
    private String descricao;

    @Column(nullable = false)
    private LocalDate dataLimite;

    private LocalDate concluidoEm;

    @Column(nullable = false)
    private boolean alertaEnviado = false;
}
