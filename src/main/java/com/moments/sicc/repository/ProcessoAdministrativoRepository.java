package com.moments.sicc.repository;

import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.ProcessoAdministrativo;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessoAdministrativoRepository extends JpaRepository<ProcessoAdministrativo, Long>,
        JpaSpecificationExecutor<ProcessoAdministrativo> {
    boolean existsByNumeroIgnoreCase(String numero);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProcessoAdministrativo p where p.id = :id")
    Optional<ProcessoAdministrativo> findByIdForUpdate(@Param("id") Long id);
    List<ProcessoAdministrativo> findByAtivoTrue();
    long countByAtivoTrueAndStatus(StatusProcesso status);

    @Override
    @EntityGraph(attributePaths = {
            "responsavel",
            "instrumento",
            "instrumento.documentoAssinado",
            "instrumento.documentoAssinadoVersao"
    })
    Page<ProcessoAdministrativo> findAll(
            Specification<ProcessoAdministrativo> specification,
            Pageable pageable);
}
