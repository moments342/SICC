package com.moments.sicc.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    private static final String ADMINISTRADOR_DIPAC = "ADMINISTRADOR_DIPAC";
    private static final String OPERADOR_DIPAC = "OPERADOR_DIPAC";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            SecurityErrorResponseWriter errorWriter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errorWriter.write(response, HttpStatus.UNAUTHORIZED, "Autenticação necessária."))
                        .accessDeniedHandler((request, response, exception) ->
                                errorWriter.write(response, HttpStatus.FORBIDDEN, "Acesso negado.")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/processos").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/public/processos").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/admin/**", "/api/v1/auditoria/**")
                        .hasRole(ADMINISTRADOR_DIPAC)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/senha")
                        .hasAnyRole(ADMINISTRADOR_DIPAC, OPERADOR_DIPAC)
                        .requestMatchers(
                                "/api/v1/setores",
                                "/api/v1/processos", "/api/v1/processos/**",
                                "/api/v1/movimentacoes", "/api/v1/movimentacoes/**",
                                "/api/v1/notificacoes", "/api/v1/notificacoes/**",
                                "/api/v1/alteracoes", "/api/v1/alteracoes/**",
                                "/api/v1/documentos", "/api/v1/documentos/**",
                                "/api/v1/dashboard",
                                "/api/v1/relatorios", "/api/v1/relatorios/**")
                        .hasAnyRole(ADMINISTRADOR_DIPAC, OPERADOR_DIPAC)
                        .anyRequest().denyAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
