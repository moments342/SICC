package com.moments.sicc.auditoria.service;

import com.moments.sicc.auditoria.domain.LogAuditoria;
import com.moments.sicc.auditoria.repository.LogAuditoriaRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogAuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;

    @Transactional
    public LogAuditoria registrar(LogAuditoria logAuditoria) {
        logAuditoria.registrar();
        return logAuditoriaRepository.save(logAuditoria);
    }
}
