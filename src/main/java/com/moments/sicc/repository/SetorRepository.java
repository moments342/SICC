package com.moments.sicc.repository;

import com.moments.sicc.domain.Setor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SetorRepository extends JpaRepository<Setor, Long> {
    boolean existsBySiglaIgnoreCase(String sigla);
    List<Setor> findByAtivoTrueOrderBySigla();
}
