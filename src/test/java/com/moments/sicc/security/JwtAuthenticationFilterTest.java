package com.moments.sicc.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.UsuarioInternoRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthenticationFilterTest {

    @Test
    void falhaPosteriorAoJwtNaoEMascaradaComoTokenInvalido() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UsuarioInternoRepository usuarios = mock(UsuarioInternoRepository.class);
        SecurityErrorResponseWriter errorWriter = mock(SecurityErrorResponseWriter.class);
        FilterChain chain = mock(FilterChain.class);
        UsuarioInterno admin = new UsuarioInterno();
        admin.setLogin("admin");
        admin.setPerfil(PerfilAcesso.ADMINISTRADOR_DIPAC);
        admin.setAtivo(true);
        admin.setSenhaTemporaria(false);
        when(jwtService.validar("token-valido"))
                .thenReturn(new JwtService.Claims("admin", "ADMINISTRADOR_DIPAC", false, 0));
        when(usuarios.findByLoginIgnoreCase("admin")).thenReturn(Optional.of(admin));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/processos");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletException falhaInterna = new ServletException("falha interna");
        doThrow(falhaInterna).when(chain).doFilter(request, response);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, usuarios, errorWriter);

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isSameAs(falhaInterna);
        verify(errorWriter, never()).write(any(), any(), any());
    }

    @Test
    void falhaDoRepositorioNaoEMascaradaComoTokenInvalido() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UsuarioInternoRepository usuarios = mock(UsuarioInternoRepository.class);
        SecurityErrorResponseWriter errorWriter = mock(SecurityErrorResponseWriter.class);
        FilterChain chain = mock(FilterChain.class);
        when(jwtService.validar("token-valido"))
                .thenReturn(new JwtService.Claims("admin", "ADMINISTRADOR_DIPAC", false, 0));
        DataAccessResourceFailureException falhaBanco =
                new DataAccessResourceFailureException("banco indisponível");
        when(usuarios.findByLoginIgnoreCase("admin")).thenThrow(falhaBanco);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/processos");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, usuarios, errorWriter);

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isSameAs(falhaBanco);
        verify(errorWriter, never()).write(any(), any(), any());
    }
}
