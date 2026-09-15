package com.moments.sicc.repository;

import com.moments.sicc.domain.InstrumentoContratual;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentoContratualRepository extends JpaRepository<InstrumentoContratual, Long> {
    interface HierarquiaInstrumento {
        Long getInstrumentoId();
        Long getProcessoId();
    }

    Optional<InstrumentoContratual> findByProcessoId(Long processoId);
    List<InstrumentoContratual> findAllByProcessoAtivoTrue();
    boolean existsByDocumentoAssinadoId(Long documentoId);

    @Query("""
            select i.id as instrumentoId,
                   i.processo.id as processoId
            from InstrumentoContratual i
            where i.id = :id
            """)
    Optional<HierarquiaInstrumento> findHierarquiaById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InstrumentoContratual i where i.id = :id")
    Optional<InstrumentoContratual> findByIdForUpdate(@Param("id") Long id);
}
