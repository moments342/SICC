package com.moments.sicc.domain;

import com.moments.sicc.domain.Enums.CampoInstrumento;
import com.moments.sicc.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "alteracoes_campos")
public class AlteracaoCampo extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alteracao_id", nullable = false)
    private AlteracaoContratual alteracao;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private CampoInstrumento campo;
    @Column(length = 3000)
    private String valorAnterior;
    @Column(length = 3000)
    private String valorNovo;
}
