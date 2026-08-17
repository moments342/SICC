package com.moments.sicc.domain;

import com.moments.sicc.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "setores")
public class Setor extends BaseEntity {
    @Column(nullable = false, unique = true, length = 30)
    private String sigla;
    @Column(nullable = false, length = 30)
    private String siglaNormalizada;
    @Column(nullable = false, length = 150)
    private String nome;
    @Column(nullable = false, length = 150)
    private String nomeNormalizado;
    @Column(nullable = false)
    private boolean ativo = true;

    public void atualizarIdentidade(IdentidadeSetor identidade) {
        sigla = identidade.sigla();
        siglaNormalizada = identidade.siglaNormalizada();
        nome = identidade.nome();
        nomeNormalizado = identidade.nomeNormalizado();
    }

    public void definirAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
