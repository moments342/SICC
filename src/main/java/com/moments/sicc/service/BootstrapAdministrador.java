package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.UsuarioInternoRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdministrador implements ApplicationRunner {
    private static final Pattern LOGIN_VALIDO = Pattern.compile("^[\\p{L}\\p{N}._-]{3,80}$");
    private static final Pattern EMAIL_VALIDO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UsuarioInternoRepository usuarios;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final String login;
    private final String password;
    private final String email;
    private final String name;

    public BootstrapAdministrador(UsuarioInternoRepository usuarios, PasswordEncoder encoder,
            AuditoriaService auditoria,
            @Value("${sicc.bootstrap.login:}") String login,
            @Value("${sicc.bootstrap.password:}") String password,
            @Value("${sicc.bootstrap.email:}") String email,
            @Value("${sicc.bootstrap.name:Administrador SICC}") String name) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.login = login;
        this.password = password;
        this.email = email;
        this.name = name;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0) return;
        List<String> ausentes = new ArrayList<>();
        if (login.isBlank()) ausentes.add("SICC_BOOTSTRAP_LOGIN");
        if (password.isBlank()) ausentes.add("SICC_BOOTSTRAP_PASSWORD");
        if (email.isBlank()) ausentes.add("SICC_BOOTSTRAP_EMAIL");
        if (!ausentes.isEmpty()) {
            throw new IllegalStateException(
                    "Banco vazio: configure as credenciais externas obrigatórias " + String.join(", ", ausentes) + ".");
        }
        validarCredenciais();
        UsuarioInterno admin = new UsuarioInterno();
        admin.setNome(name);
        admin.setEmail(email.toLowerCase());
        admin.setLogin(login.toLowerCase());
        admin.setSenhaHash(encoder.encode(password));
        admin.setPerfil(PerfilAcesso.ADMINISTRADOR_DIPAC);
        admin.setSenhaTemporaria(true);
        usuarios.save(admin);
        auditoria.registrarNaTransacaoAtual(admin, "CRIAR_USUARIO", "USUARIO_INTERNO", admin.getId(),
                true, "Primeiro Administrador DIPAC criado pelo bootstrap.", null);
    }

    private void validarCredenciais() {
        if (!LOGIN_VALIDO.matcher(login).matches()) {
            throw credencialInvalida(
                    "SICC_BOOTSTRAP_LOGIN", "use de 3 a 80 letras, números, ponto, hífen ou sublinhado");
        }
        if (email.length() > 150 || !EMAIL_VALIDO.matcher(email).matches()) {
            throw credencialInvalida("SICC_BOOTSTRAP_EMAIL", "informe um e-mail válido com até 150 caracteres");
        }
        if (name.isBlank() || name.length() > 150) {
            throw credencialInvalida("SICC_BOOTSTRAP_NAME", "informe um nome com até 150 caracteres");
        }
        PoliticaSenha.motivoInvalidez(password).ifPresent(motivo -> {
            throw credencialInvalida("SICC_BOOTSTRAP_PASSWORD", motivo);
        });
    }

    private IllegalStateException credencialInvalida(String variavel, String orientacao) {
        return new IllegalStateException(
                "Banco vazio: a credencial externa " + variavel + " é inválida; " + orientacao + ".");
    }
}
