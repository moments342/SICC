package com.moments.sicc.service;

import com.moments.sicc.api.ApiDtos.LoginResponse;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.UsuarioInternoRepository;
import com.moments.sicc.security.JwtService;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.UnauthorizedException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutenticacaoService {
    private final UsuarioInternoRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditoriaService auditoria;

    @Transactional
    public LoginResponse autenticar(String login, String senha, String ip) {
        UsuarioInterno usuario = usuarios.findByLoginIgnoreCase(login).orElse(null);
        if (usuario == null || !usuario.isAtivo() || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            auditoria.registrar(usuario, "LOGIN", "USUARIO_INTERNO", usuario == null ? null : usuario.getId(),
                    false, "Credenciais inválidas.", ip);
            throw new UnauthorizedException("Credenciais inválidas.");
        }
        usuario.setUltimoAcessoEm(LocalDateTime.now());
        usuarios.save(usuario);
        auditoria.registrar(usuario, "LOGIN", "USUARIO_INTERNO", usuario.getId(), true, "Login realizado.", ip);
        return new LoginResponse(jwtService.gerar(usuario), usuario.getPerfil().name(), usuario.isSenhaTemporaria());
    }

    @Transactional
    public void trocarSenha(UsuarioInterno usuario, String atual, String nova, String ip) {
        if (!passwordEncoder.matches(atual, usuario.getSenhaHash())) {
            throw new DomainException("Senha atual inválida.");
        }
        validarSenha(nova);
        usuario.setSenhaHash(passwordEncoder.encode(nova));
        usuario.setSenhaTemporaria(false);
        usuario.invalidarSessoes();
        usuarios.save(usuario);
        auditoria.registrar(usuario, "TROCAR_SENHA", "USUARIO_INTERNO", usuario.getId(), true,
                "Senha permanente definida.", ip);
    }

    public void validarSenha(String senha) {
        PoliticaSenha.motivoInvalidez(senha).ifPresent(motivo -> {
            throw new DomainException(motivo);
        });
    }
}
