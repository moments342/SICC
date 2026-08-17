package com.moments.sicc.service;

import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.UsuarioInternoRepository;
import com.moments.sicc.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdentidadeService {
    private final UsuarioInternoRepository usuarios;

    public UsuarioInterno atual() {
        String login = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarios.findByLoginIgnoreCase(login)
                .orElseThrow(() -> new NotFoundException("Usuário autenticado não encontrado."));
    }
}
