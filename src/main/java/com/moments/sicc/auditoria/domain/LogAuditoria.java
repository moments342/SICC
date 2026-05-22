package com.moments.sicc.auditoria.domain;

import com.moments.sicc.shared.domain.BaseEntity;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.usuario.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "logs_auditoria")
public class LogAuditoria extends BaseEntity {

    @Column(nullable = false)
    private LocalDateTime dataHora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 120)
    private String acao;

    @Column(nullable = false, length = 120)
    private String entidadeAfetada;

    @Column(nullable = false)
    private Long idEntidadeAfetada;

    @Column(nullable = false, length = 1000)
    private String detalhes;

    @Column(length = 80)
    private String ipOrigem;

    public void registrar() {
        if (acao == null || acao.isBlank()) {
            throw new DomainException("Acao de auditoria e obrigatoria.");
        }
        if (entidadeAfetada == null || entidadeAfetada.isBlank()) {
            throw new DomainException("Entidade afetada e obrigatoria.");
        }
        if (idEntidadeAfetada == null) {
            throw new DomainException("Identificador da entidade afetada e obrigatorio.");
        }
        if (detalhes == null || detalhes.isBlank()) {
            detalhes = gerarDescricao();
        }
        if (dataHora == null) {
            dataHora = LocalDateTime.now();
        }
    }

    public String gerarDescricao() {
        String login = usuario == null ? "sistema" : usuario.getLogin();
        return login + " executou " + acao + " em " + entidadeAfetada + " #" + idEntidadeAfetada;
    }
}
