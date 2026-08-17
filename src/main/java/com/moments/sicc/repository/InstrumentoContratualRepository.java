package com.moments.sicc.repository;

import com.moments.sicc.domain.InstrumentoContratual;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentoContratualRepository extends JpaRepository<InstrumentoContratual, Long> {
    Optional<InstrumentoContratual> findByProcessoId(Long processoId);
    List<InstrumentoContratual> findAllByProcessoAtivoTrue();
}
