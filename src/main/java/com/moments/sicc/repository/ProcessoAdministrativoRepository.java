package com.moments.sicc.repository;

import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.ProcessoAdministrativo;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessoAdministrativoRepository extends JpaRepository<ProcessoAdministrativo, Long> {
    boolean existsByNumeroIgnoreCase(String numero);
    Optional<ProcessoAdministrativo> findByIdAndAtivoTrue(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProcessoAdministrativo p where p.id = :id and p.ativo = true")
    Optional<ProcessoAdministrativo> findAtivoByIdForUpdate(@Param("id") Long id);
    Page<ProcessoAdministrativo> findByAtivoTrueAndNumeroContainingIgnoreCase(String numero, Pageable pageable);
    List<ProcessoAdministrativo> findByAtivoTrue();
    long countByAtivoTrueAndStatus(StatusProcesso status);
}
