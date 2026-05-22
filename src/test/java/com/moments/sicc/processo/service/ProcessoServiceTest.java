package com.moments.sicc.processo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moments.sicc.processo.domain.Processo;
import com.moments.sicc.processo.repository.ProcessoRepository;
import com.moments.sicc.shared.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessoServiceTest {

    @Mock
    private ProcessoRepository processoRepository;

    @InjectMocks
    private ProcessoService processoService;

    @Test
    void cadastrarSalvaProcessoValido() {
        Processo processo = processoValido();
        when(processoRepository.existsByNumeroProcesso(processo.getNumeroProcesso())).thenReturn(false);
        when(processoRepository.save(processo)).thenReturn(processo);

        Processo salvo = processoService.cadastrar(processo);

        assertThat(salvo).isSameAs(processo);
        verify(processoRepository).save(processo);
    }

    @Test
    void cadastrarBloqueiaNumeroDuplicado() {
        Processo processo = processoValido();
        when(processoRepository.existsByNumeroProcesso(processo.getNumeroProcesso())).thenReturn(true);

        assertThatThrownBy(() -> processoService.cadastrar(processo))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Ja existe processo");
    }

    @Test
    void cadastrarValidaCamposObrigatoriosAntesDeSalvar() {
        Processo processo = processoValido();
        processo.setObjeto("");

        assertThatThrownBy(() -> processoService.cadastrar(processo))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Objeto do processo");
    }

    private Processo processoValido() {
        Processo processo = new Processo();
        processo.setNumeroProcesso("23005.000001/2026-10");
        processo.setTipoInstrumento("CONTRATO");
        processo.setObjeto("Contrato de apoio institucional");
        return processo;
    }
}
