package com.moments.sicc.domain.entity;

import com.moments.sicc.domain.enums.TipoInstrumento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fluxos_tramitacao")
public class FluxoTramitacao extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoInstrumento tipoInstrumento;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false)
    private boolean ativo = true;

    @OneToMany(mappedBy = "fluxo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EtapaFluxo> etapas = new ArrayList<>();
}
