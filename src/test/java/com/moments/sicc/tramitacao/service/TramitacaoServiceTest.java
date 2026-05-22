package com.moments.sicc.tramitacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moments.sicc.processo.domain.Processo;
import com.moments.sicc.processo.repository.ProcessoRepository;
import com.moments.sicc.tramitacao.domain.Tramitacao;
import com.moments.sicc.tramitacao.repository.TramitacaoRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TramitacaoServiceTest {

    @Mock
    private TramitacaoRepository tramitacaoRepository;

    @Mock
    private ProcessoRepository processoRepository;

    @InjectMocks
    private TramitacaoService tramitacaoService;

    @Test
    void registrarAtualizaProcessoESalvaTramitacao() {
        Processo processo = processoValido();
        Tramitacao tramitacao = tramitacaoValida();
        when(processoRepository.findById(1L)).thenReturn(Optional.of(processo));
        when(tramitacaoRepository.save(tramitacao)).thenReturn(tramitacao);

        Tramitacao salva = tramitacaoService.registrar(1L, tramitacao);

        assertThat(salva).isSameAs(tramitacao);
        assertThat(processo.getStatusAtual()).isEqualTo("EM_ANALISE");
        assertThat(processo.getEtapaAtual()).isEqualTo("Analise administrativa");
        verify(processoRepository).save(processo);
        verify(tramitacaoRepository).save(tramitacao);
    }

    private Processo processoValido() {
        Processo processo = new Processo();
        processo.setNumeroProcesso("23005.000001/2026-10");
        processo.setTipoInstrumento("CONTRATO");
        processo.setObjeto("Contrato de apoio institucional");
        processo.setStatusAtual("CADASTRADO");
        return processo;
    }

    private Tramitacao tramitacaoValida() {
        Tramitacao tramitacao = new Tramitacao();
        tramitacao.setSetor("DIPAC");
        tramitacao.setResponsavel("Maria");
        tramitacao.setAcaoRealizada("Encaminhado para analise");
        tramitacao.setEtapa("Analise administrativa");
        tramitacao.setStatusNovo("EM_ANALISE");
        return tramitacao;
    }
}
