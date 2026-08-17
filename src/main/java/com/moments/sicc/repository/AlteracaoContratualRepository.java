package com.moments.sicc.repository;

import com.moments.sicc.domain.AlteracaoContratual;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlteracaoContratualRepository extends JpaRepository<AlteracaoContratual, Long> {
    List<AlteracaoContratual> findByInstrumentoIdOrderByDataEfetivacaoAscOrdemOficialAsc(Long instrumentoId);
    boolean existsByDocumentoAssinadoId(Long documentoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AlteracaoContratual a where a.id = :id")
    Optional<AlteracaoContratual> findByIdForUpdate(@Param("id") Long id);
}
