package com.moments.sicc.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "setores")
public class Setor extends BaseEntity {

    @NotBlank
    @Column(nullable = false, unique = true, length = 30)
    private String sigla;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private boolean unidadeImportante = false;

    @OneToMany(mappedBy = "setorPrincipal")
    private List<Usuario> usuarios = new ArrayList<>();
}
