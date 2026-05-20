package com.moments.sicc.domain.entity;

import com.moments.sicc.domain.enums.StatusProcesso;
import com.moments.sicc.domain.enums.TipoInstrumento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "processos_contratuais")
public class ProcessoContratual extends BaseEntity {

    @NotBlank
    @Column(nullable = false, unique = true, length = 50)
    private String numeroProcesso;

    @Column(length = 50)
    private String numeroInstrumento;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String objeto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoInstrumento tipoInstrumento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StatusProcesso status = StatusProcesso.CADASTRADO;

    @Column(nullable = false)
    private LocalDate dataCadastro = LocalDate.now();

    private LocalDate dataInicioVigencia;

    private LocalDate dataFimVigencia;

    @Column(nullable = false)
    private boolean publicoExternamente = false;

    @Column(length = 500)
    private String resumoPublico;

    @Column(length = 1000)
    private String observacoesInternas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "setor_atual_id")
    private Setor setorAtual;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etapa_atual_id")
    private EtapaFluxo etapaAtual;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fluxo_tramitacao_id")
    private FluxoTramitacao fluxoTramitacao;

    @OneToMany(mappedBy = "processo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovimentacaoProcesso> movimentacoes = new ArrayList<>();

    @OneToMany(mappedBy = "processo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrazoProcesso> prazos = new ArrayList<>();

    @OneToMany(mappedBy = "processo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentoAnexo> documentos = new ArrayList<>();

    @OneToMany(mappedBy = "processo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResponsavelProcesso> responsaveis = new ArrayList<>();

    @OneToMany(mappedBy = "processo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Notificacao> notificacoes = new ArrayList<>();
}
