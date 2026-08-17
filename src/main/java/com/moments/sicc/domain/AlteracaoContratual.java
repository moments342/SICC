package com.moments.sicc.domain;

import com.moments.sicc.domain.Enums.EstadoAlteracao;
import com.moments.sicc.domain.Enums.OperacaoAlteracao;
import com.moments.sicc.domain.Enums.TipoAlteracao;
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
@Table(name = "alteracoes_contratuais")
public class AlteracaoContratual extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrumento_id", nullable = false)
    private InstrumentoContratual instrumento;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAlteracao tipo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoAlteracao estado = EstadoAlteracao.RASCUNHO;
    @Column(nullable = false, length = 80)
    private String numeroOficial;
    private Integer ordemOficial;
    private LocalDate dataEfetivacao;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referencia_id")
    private AlteracaoContratual referencia;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OperacaoAlteracao operacao = OperacaoAlteracao.ORIGINAL;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_assinado_id")
    private Documento documentoAssinado;
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
