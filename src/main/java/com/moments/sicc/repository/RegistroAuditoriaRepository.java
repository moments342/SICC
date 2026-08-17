package com.moments.sicc.repository;

import com.moments.sicc.domain.RegistroAuditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {
    List<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByCriadoEmDesc(String entidade, Long entidadeId);
}
