package com.moments.sicc.domain;

import com.moments.sicc.domain.Enums.StatusProcesso;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "processos_administrativos")
public class ProcessoAdministrativo extends BaseEntity {
    @Column(nullable = false, unique = true, length = 60)
    private String numero;
    @Column(nullable = false, length = 150)
    private String origem;
    @Column(length = 80)
    private String numeroProjeto;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StatusProcesso status = StatusProcesso.EM_FORMALIZACAO;
    @Column(nullable = false, updatable = false)
    private LocalDate dataCadastro = LocalDate.now();
    @Column(nullable = false)
    private boolean ativo = true;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private UsuarioInterno responsavel;
}
