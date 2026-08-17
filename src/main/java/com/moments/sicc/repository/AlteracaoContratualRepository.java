package com.moments.sicc.repository;

import com.moments.sicc.domain.AlteracaoContratual;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlteracaoContratualRepository extends JpaRepository<AlteracaoContratual, Long> {
    List<AlteracaoContratual> findByInstrumentoIdOrderByDataEfetivacaoAscOrdemOficialAsc(Long instrumentoId);
}
