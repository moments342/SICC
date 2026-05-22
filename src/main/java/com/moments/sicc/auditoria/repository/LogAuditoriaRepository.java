package com.moments.sicc.auditoria.repository;

import com.moments.sicc.auditoria.domain.LogAuditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Long> {

    List<LogAuditoria> findByEntidadeAfetadaAndIdEntidadeAfetada(String entidadeAfetada, Long idEntidadeAfetada);
}
