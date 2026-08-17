package com.moments.sicc.domain;

import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "instrumentos_contratuais")
public class InstrumentoContratual extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false, unique = true)
    private ProcessoAdministrativo processo;
    @Column(nullable = false, length = 60)
    private String numero;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private TipoInstrumento tipo;
    @Column(nullable = false, length = 1000)
    private String objeto;
    @Column(length = 2000)
    private String descricao;
    @Column(nullable = false, length = 150)
    private String natureza;
    @Column(nullable = false, length = 150)
    private String coordenador;
    @Column(nullable = false, length = 2000)
    private String participes;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorAtual;
    @Column(nullable = false)
    private LocalDate vigenciaContratualFinal;
    private LocalDate vigenciaTedFinal;
    @Column(nullable = false, updatable = false)
    private LocalDate dataFormalizacao;
}
