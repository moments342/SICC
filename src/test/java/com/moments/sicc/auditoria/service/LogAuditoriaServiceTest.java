package com.moments.sicc.auditoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moments.sicc.auditoria.domain.LogAuditoria;
import com.moments.sicc.auditoria.repository.LogAuditoriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogAuditoriaServiceTest {

    @Mock
    private LogAuditoriaRepository logAuditoriaRepository;

    @InjectMocks
    private LogAuditoriaService logAuditoriaService;

    @Test
    void registrarPreparaLogESalva() {
        LogAuditoria log = new LogAuditoria();
        log.setAcao("CADASTRAR");
        log.setEntidadeAfetada("Processo");
        log.setIdEntidadeAfetada(1L);
        log.setDetalhes("");
        when(logAuditoriaRepository.save(log)).thenReturn(log);

        LogAuditoria salvo = logAuditoriaService.registrar(log);

        assertThat(salvo.getDataHora()).isNotNull();
        assertThat(salvo.getDetalhes()).contains("executou CADASTRAR");
        verify(logAuditoriaRepository).save(log);
    }
}
