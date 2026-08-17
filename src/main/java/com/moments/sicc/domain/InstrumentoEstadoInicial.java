package com.moments.sicc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "instrumentos_estados_iniciais")
public class InstrumentoEstadoInicial {
    @Id
    @Column(name = "instrumento_id")
    private Long instrumentoId;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrumento_id", nullable = false)
    private InstrumentoContratual instrumento;
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

    public static InstrumentoEstadoInicial copiarDe(InstrumentoContratual instrumento) {
        InstrumentoEstadoInicial estado = new InstrumentoEstadoInicial();
        estado.instrumento = instrumento;
        estado.objeto = instrumento.getObjeto();
        estado.descricao = instrumento.getDescricao();
        estado.natureza = instrumento.getNatureza();
        estado.coordenador = instrumento.getCoordenador();
        estado.participes = instrumento.getParticipes();
        estado.valorAtual = instrumento.getValorAtual();
        estado.vigenciaContratualFinal = instrumento.getVigenciaContratualFinal();
        estado.vigenciaTedFinal = instrumento.getVigenciaTedFinal();
        return estado;
    }
}
