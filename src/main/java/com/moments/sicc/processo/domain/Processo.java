package com.moments.sicc.processo.domain;

import com.moments.sicc.documento.domain.DocumentoAnexo;
import com.moments.sicc.shared.domain.BaseEntity;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.tramitacao.domain.Tramitacao;
import com.moments.sicc.usuario.domain.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "processos")
public class Processo extends BaseEntity {

    private static final long DIAS_ALERTA_VENCIMENTO = 30;

    @NotBlank
    @Column(nullable = false, unique = true, length = 50)
    private String numeroProcesso;

    @NotBlank
    @Column(nullable = false, length = 80)
    private String tipoInstrumento;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String objeto;

    @Column(length = 1000)
    private String descricao;

    @Column(length = 120)
    private String origem;

    @Column(length = 120)
    private String natureza;

    @Column(length = 150)
    private String coordenador;

    @Column(length = 1000)
    private String participes;

    @Column(length = 50)
    private String numContrato;

    @Column(length = 50)
    private String numProjeto;

    private LocalDate vigenciaContrato;

    private LocalDate vigenciaTed;

    @Column(precision = 19, scale = 2)
    private BigDecimal valorTotal;

    @NotBlank
    @Column(nullable = false, length = 60)
    private String statusAtual = "CADASTRADO";

    @Column(length = 120)
    private String etapaAtual;

    @Column(nullable = false)
    private boolean publicoSimplificado = false;

    @Column(length = 1000)
    private String anexos;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false, updatable = false)
    private LocalDate dataCadastro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_gerente_id")
    private Usuario usuarioGerente;

    @OneToMany(mappedBy = "processo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Tramitacao> tramitacoes = new ArrayList<>();

    @OneToMany(mappedBy = "processo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentoAnexo> documentos = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (dataCadastro == null) {
            dataCadastro = LocalDate.now();
        }
    }

    public void cadastrar() {
        validarCamposObrigatorios();
        ativo = true;
        if (dataCadastro == null) {
            dataCadastro = LocalDate.now();
        }
        if (statusAtual == null || statusAtual.isBlank()) {
            statusAtual = "CADASTRADO";
        }
    }

    public void editarDados(String objeto, String descricao) {
        if (objeto == null || objeto.isBlank()) {
            throw new DomainException("Objeto do processo e obrigatorio.");
        }
        this.objeto = objeto;
        this.descricao = descricao;
    }

    public void atualizarStatus(String novoStatus) {
        if (novoStatus == null || novoStatus.isBlank()) {
            throw new DomainException("Status e obrigatorio.");
        }
        statusAtual = novoStatus;
    }

    public void atualizarEtapa(String novaEtapa) {
        if (novaEtapa == null || novaEtapa.isBlank()) {
            throw new DomainException("Etapa e obrigatoria.");
        }
        etapaAtual = novaEtapa;
    }

    public void validarCamposObrigatorios() {
        if (numeroProcesso == null || numeroProcesso.isBlank()) {
            throw new DomainException("Numero do processo e obrigatorio.");
        }
        if (tipoInstrumento == null || tipoInstrumento.isBlank()) {
            throw new DomainException("Tipo de instrumento e obrigatorio.");
        }
        if (objeto == null || objeto.isBlank()) {
            throw new DomainException("Objeto do processo e obrigatorio.");
        }
    }

    public boolean verificarDuplicidade(String outroNumeroProcesso) {
        return numeroProcesso != null && Objects.equals(numeroProcesso, outroNumeroProcesso);
    }

    public boolean estaVencido() {
        LocalDate hoje = LocalDate.now();
        return estaDataVencida(vigenciaContrato, hoje) || estaDataVencida(vigenciaTed, hoje);
    }

    public boolean estaProximoDoVencimento() {
        LocalDate hoje = LocalDate.now();
        return estaDataProxima(vigenciaContrato, hoje) || estaDataProxima(vigenciaTed, hoje);
    }

    private boolean estaDataVencida(LocalDate data, LocalDate hoje) {
        return data != null && data.isBefore(hoje);
    }

    private boolean estaDataProxima(LocalDate data, LocalDate hoje) {
        if (data == null || data.isBefore(hoje)) {
            return false;
        }
        long dias = ChronoUnit.DAYS.between(hoje, data);
        return dias <= DIAS_ALERTA_VENCIMENTO;
    }
}
