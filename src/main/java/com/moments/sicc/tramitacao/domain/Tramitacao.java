package com.moments.sicc.tramitacao.domain;

import com.moments.sicc.processo.domain.Processo;
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
@Table(name = "tramitacoes")
public class Tramitacao extends BaseEntity {

    @Column(nullable = false)
    private LocalDateTime dataMovimentacao;

    @Column(nullable = false, length = 120)
    private String setor;

    @Column(nullable = false, length = 150)
    private String responsavel;

    @Column(nullable = false, length = 200)
    private String acaoRealizada;

    @Column(length = 1000)
    private String observacao;

    @Column(nullable = false, length = 120)
    private String etapa;

    @Column(length = 60)
    private String statusAnterior;

    @Column(nullable = false, length = 60)
    private String statusNovo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    private Processo processo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_usuario_id")
    private Usuario responsavelUsuario;

    public void registrar() {
        if (dataMovimentacao == null) {
            dataMovimentacao = LocalDateTime.now();
        }
        validarCamposObrigatorios();
    }

    public void editar(String observacao, String acaoRealizada) {
        if (acaoRealizada == null || acaoRealizada.isBlank()) {
            throw new DomainException("Acao realizada e obrigatoria.");
        }
        this.observacao = observacao;
        this.acaoRealizada = acaoRealizada;
    }

    public void aplicarAoProcesso(Processo processo) {
        if (processo == null) {
            throw new DomainException("Processo e obrigatorio para aplicar tramitacao.");
        }
        validarCamposObrigatorios();
        statusAnterior = processo.getStatusAtual();
        processo.atualizarStatus(statusNovo);
        processo.atualizarEtapa(etapa);
        this.processo = processo;
    }

    public String resumoMovimentacao() {
        return etapa + " - " + acaoRealizada + " por " + responsavel;
    }

    private void validarCamposObrigatorios() {
        if (setor == null || setor.isBlank()) {
            throw new DomainException("Setor e obrigatorio.");
        }
        if (responsavel == null || responsavel.isBlank()) {
            throw new DomainException("Responsavel e obrigatorio.");
        }
        if (acaoRealizada == null || acaoRealizada.isBlank()) {
            throw new DomainException("Acao realizada e obrigatoria.");
        }
        if (etapa == null || etapa.isBlank()) {
            throw new DomainException("Etapa e obrigatoria.");
        }
        if (statusNovo == null || statusNovo.isBlank()) {
            throw new DomainException("Novo status e obrigatorio.");
        }
    }
}
