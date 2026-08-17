package com.moments.sicc.domain;

import com.moments.sicc.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "setores")
public class Setor extends BaseEntity {
    @Column(nullable = false, unique = true, length = 30)
    private String sigla;
    @Column(nullable = false, length = 150)
    private String nome;
    @Column(nullable = false)
    private boolean ativo = true;
}
