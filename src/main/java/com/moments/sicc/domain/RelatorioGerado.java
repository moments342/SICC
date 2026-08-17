package com.moments.sicc.domain;

import com.moments.sicc.domain.Enums.FormatoRelatorio;
import com.moments.sicc.domain.Enums.TipoRelatorio;
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
@Table(name = "relatorios_gerados")
public class RelatorioGerado extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoRelatorio tipo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FormatoRelatorio formato;
    @Column(length = 2000)
    private String filtros;
    @Column(nullable = false, length = 500)
    private String chaveArmazenamento;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private UsuarioInterno criadoPor;
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
