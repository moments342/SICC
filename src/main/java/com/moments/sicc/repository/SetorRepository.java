package com.moments.sicc.repository;

import com.moments.sicc.domain.Setor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SetorRepository extends JpaRepository<Setor, Long> {
    boolean existsBySiglaNormalizada(String siglaNormalizada);
    boolean existsByNomeNormalizado(String nomeNormalizado);
    boolean existsBySiglaNormalizadaAndIdNot(String siglaNormalizada, Long id);
    boolean existsByNomeNormalizadoAndIdNot(String nomeNormalizado, Long id);
    List<Setor> findAllByOrderBySiglaAsc();
    List<Setor> findByAtivoTrueOrderBySigla();
}
