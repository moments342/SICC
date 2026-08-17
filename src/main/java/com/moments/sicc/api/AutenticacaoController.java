package com.moments.sicc.api;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.service.AutenticacaoService;
import com.moments.sicc.service.IdentidadeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AutenticacaoController {
    private final AutenticacaoService autenticacao;
    private final IdentidadeService identidade;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return autenticacao.autenticar(request.login(), request.senha(), http.getRemoteAddr());
    }

    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@Valid @RequestBody TrocarSenhaRequest request, HttpServletRequest http) {
        autenticacao.trocarSenha(identidade.atual(), request.senhaAtual(), request.novaSenha(), http.getRemoteAddr());
    }
}
