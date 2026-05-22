package com.moments.sicc.usuario.domain;

import com.moments.sicc.auditoria.domain.LogAuditoria;
import com.moments.sicc.documento.domain.DocumentoAnexo;
import com.moments.sicc.processo.domain.Processo;
import com.moments.sicc.shared.domain.BaseEntity;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.tramitacao.domain.Tramitacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuarios")
public class Usuario extends BaseEntity {

    @NotBlank
    @Column(nullable = false, length = 150)
    private String nome;

    @Email
    @NotBlank
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank
    @Column(nullable = false, unique = true, length = 80)
    private String login;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String senhaHash;

    @NotBlank
    @Column(nullable = false, length = 40)
    private String perfil;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime ultimoAcesso;

    @OneToMany(mappedBy = "usuarioGerente")
    private List<Processo> processosGerenciados = new ArrayList<>();

    @OneToMany(mappedBy = "responsavelUsuario")
    private List<Tramitacao> tramitacoes = new ArrayList<>();

    @OneToMany(mappedBy = "enviadoPor")
    private List<DocumentoAnexo> documentosAnexados = new ArrayList<>();

    @OneToMany(mappedBy = "usuario")
    private List<LogAuditoria> logsAuditoria = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
    }

    public boolean autenticar(String senhaInformada) {
        if (!ativo) {
            return false;
        }
        boolean autenticado = senhaConfere(senhaInformada);
        if (autenticado) {
            ultimoAcesso = LocalDateTime.now();
        }
        return autenticado;
    }

    public void definirSenha(String senha) {
        if (senha == null || senha.isBlank()) {
            throw new DomainException("Senha e obrigatoria.");
        }
        senhaHash = gerarHashSenha(senha);
    }

    public void alterarSenha(String senhaAtual, String novaSenha) {
        if (!senhaConfere(senhaAtual)) {
            throw new DomainException("Senha atual invalida.");
        }
        definirSenha(novaSenha);
    }

    public void desativar() {
        ativo = false;
    }

    public boolean temPermissao(String acao) {
        if (!ativo || acao == null || acao.isBlank()) {
            return false;
        }
        String perfilNormalizado = perfil == null ? "" : perfil.trim().toUpperCase();
        String acaoNormalizada = acao.trim().toUpperCase();

        if ("ADMINISTRADOR".equals(perfilNormalizado)) {
            return true;
        }
        if ("SERVIDOR_INTERNO".equals(perfilNormalizado)) {
            return !acaoNormalizada.startsWith("GERENCIAR_USUARIO");
        }
        if ("DOCENTE".equals(perfilNormalizado)) {
            return acaoNormalizada.startsWith("CONSULTAR");
        }
        if ("PUBLICO_EXTERNO".equals(perfilNormalizado)) {
            return "CONSULTAR_PUBLICO".equals(acaoNormalizada);
        }
        return false;
    }

    private boolean senhaConfere(String senhaInformada) {
        if (senhaInformada == null || senhaInformada.isBlank() || senhaHash == null) {
            return false;
        }
        return senhaHash.equals(gerarHashSenha(senhaInformada));
    }

    private String gerarHashSenha(String senha) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(senha.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new DomainException("Nao foi possivel gerar o hash da senha.");
        }
    }
}
