package com.moments.sicc.repository;

import com.moments.sicc.domain.RegistroAuditoria;
import java.util.List;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RegistroAuditoriaRepository
        extends Repository<RegistroAuditoria, Long>, JpaSpecificationExecutor<RegistroAuditoria> {
    RegistroAuditoria save(RegistroAuditoria registro);
    List<RegistroAuditoria> findAll();
    List<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByCriadoEmDesc(String entidade, Long entidadeId);
}
