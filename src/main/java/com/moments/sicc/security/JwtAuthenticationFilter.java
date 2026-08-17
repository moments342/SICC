package com.moments.sicc.security;

import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.UsuarioInternoRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UsuarioInternoRepository usuarios;
    private final SecurityErrorResponseWriter errorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        JwtService.Claims claims;
        try {
            claims = jwtService.validar(authorization.substring(7));
        } catch (IllegalArgumentException e) {
            rejeitarTokenInvalido(response);
            return;
        }
        UsuarioInterno usuario = usuarios.findByLoginIgnoreCase(claims.login())
                .filter(UsuarioInterno::isAtivo)
                .orElse(null);
        if (usuario == null) {
            rejeitarTokenInvalido(response);
            return;
        }
        boolean tokenDesatualizado = claims.versaoAcesso() != usuario.getVersaoAcesso()
                || !claims.perfil().equals(usuario.getPerfil().name())
                || claims.senhaTemporaria() != usuario.isSenhaTemporaria();
        if (tokenDesatualizado) {
            rejeitarTokenInvalido(response);
            return;
        }
        if (claims.senhaTemporaria() && !request.getRequestURI().equals("/api/v1/auth/senha")) {
            errorWriter.write(response, HttpStatus.FORBIDDEN, "Troca de senha obrigatória.");
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                usuario.getLogin(), null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private void rejeitarTokenInvalido(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        errorWriter.write(response, HttpStatus.UNAUTHORIZED, "Token inválido ou expirado.");
    }
}
