package com.moments.sicc.usuario.repository;

import com.moments.sicc.usuario.domain.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByLogin(String login);

    boolean existsByLogin(String login);

    boolean existsByEmail(String email);
}
