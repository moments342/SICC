package com.moments.sicc.repository;

import com.moments.sicc.domain.UsuarioInterno;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioInternoRepository extends JpaRepository<UsuarioInterno, Long> {
    Optional<UsuarioInterno> findByLoginIgnoreCase(String login);
    boolean existsByLoginIgnoreCase(String login);
    boolean existsByEmailIgnoreCase(String email);
    List<UsuarioInterno> findByAtivoTrue();
    List<UsuarioInterno> findByAtivoTrueOrderByNomeAsc();
}
