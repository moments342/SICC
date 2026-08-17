package com.moments.sicc.domain;

import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuarios_internos")
public class UsuarioInterno extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String nome;
    @Column(nullable = false, unique = true, length = 150)
    private String email;
    @Column(nullable = false, unique = true, updatable = false, length = 80)
    private String login;
    @Column(nullable = false, length = 100)
    private String senhaHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PerfilAcesso perfil;
    @Column(nullable = false)
    private boolean ativo = true;
    @Column(nullable = false)
    private boolean senhaTemporaria = true;
    @Column(nullable = false)
    private long versaoAcesso;
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime ultimoAcessoEm;

    public void invalidarSessoes() {
        versaoAcesso++;
    }
}
