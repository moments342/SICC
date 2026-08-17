package com.moments.sicc.service;

import com.moments.sicc.api.ApiDtos.CriarUsuarioRequest;
import com.moments.sicc.api.ApiDtos.UsuarioResponse;
import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.UsuarioInternoRepository;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdministracaoUsuarioService {
    private final UsuarioInternoRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final AutenticacaoService autenticacao;
    private final AuditoriaService auditoria;

    @Transactional
    public UsuarioResponse criar(CriarUsuarioRequest request, UsuarioInterno autor, String ip) {
        String login = request.login().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByLoginIgnoreCase(login)) {
            throw new DomainException("Já existe usuário com este login.");
        }
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new DomainException("Já existe usuário com este e-mail.");
        }
        autenticacao.validarSenha(request.senhaTemporaria());
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setNome(request.nome().trim());
        usuario.setEmail(email);
        usuario.setLogin(login);
        usuario.setSenhaHash(passwordEncoder.encode(request.senhaTemporaria()));
        usuario.setPerfil(request.perfil());
        usuario.setSenhaTemporaria(true);
        try {
            usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new DomainException("Já existe usuário com este login ou e-mail.");
        }
        auditoria.registrarNaTransacaoAtual(
                autor, "CRIAR_USUARIO", "USUARIO_INTERNO", usuario.getId(), true, null, ip);
        return resposta(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarios.findAll().stream().map(this::resposta).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse detalhar(Long id) {
        return resposta(usuario(id));
    }

    @Transactional
    public UsuarioResponse redefinirSenha(Long id, String senha, UsuarioInterno autor, String ip) {
        autenticacao.validarSenha(senha);
        UsuarioInterno usuario = usuario(id);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setSenhaTemporaria(true);
        usuario.invalidarSessoes();
        auditoria.registrarNaTransacaoAtual(
                autor, "REDEFINIR_SENHA", "USUARIO_INTERNO", id, true, null, ip);
        return resposta(usuario);
    }

    @Transactional
    public UsuarioResponse definirAtivo(Long id, boolean ativo, UsuarioInterno autor, String ip) {
        UsuarioInterno usuario = usuario(id);
        if (Objects.equals(usuario.getId(), autor.getId()) && !ativo) {
            throw new DomainException("O administrador não pode desativar a própria conta.");
        }
        if (usuario.isAtivo() == ativo) {
            return resposta(usuario);
        }
        usuario.setAtivo(ativo);
        usuario.invalidarSessoes();
        auditoria.registrarNaTransacaoAtual(autor, ativo ? "REATIVAR_USUARIO" : "DESATIVAR_USUARIO",
                "USUARIO_INTERNO", id, true, null, ip);
        return resposta(usuario);
    }

    @Transactional
    public UsuarioResponse definirPerfil(
            Long id, PerfilAcesso perfil, UsuarioInterno autor, String ip) {
        UsuarioInterno usuario = usuario(id);
        if (usuario.getPerfil() == perfil) {
            return resposta(usuario);
        }
        if (Objects.equals(usuario.getId(), autor.getId())) {
            throw new DomainException("O administrador não pode alterar o próprio perfil.");
        }
        usuario.setPerfil(perfil);
        usuario.invalidarSessoes();
        auditoria.registrarNaTransacaoAtual(
                autor, "ALTERAR_PERFIL", "USUARIO_INTERNO", id, true, perfil.name(), ip);
        return resposta(usuario);
    }

    private UsuarioInterno usuario(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }

    private UsuarioResponse resposta(UsuarioInterno usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getLogin(),
                usuario.getPerfil(),
                usuario.isAtivo(),
                usuario.isSenhaTemporaria());
    }
}
