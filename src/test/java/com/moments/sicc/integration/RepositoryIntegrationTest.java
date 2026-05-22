package com.moments.sicc.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.moments.sicc.auditoria.domain.LogAuditoria;
import com.moments.sicc.auditoria.repository.LogAuditoriaRepository;
import com.moments.sicc.documento.domain.DocumentoAnexo;
import com.moments.sicc.documento.repository.DocumentoAnexoRepository;
import com.moments.sicc.processo.domain.Processo;
import com.moments.sicc.processo.repository.ProcessoRepository;
import com.moments.sicc.tramitacao.domain.Tramitacao;
import com.moments.sicc.tramitacao.repository.TramitacaoRepository;
import com.moments.sicc.usuario.domain.Usuario;
import com.moments.sicc.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class RepositoryIntegrationTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private TramitacaoRepository tramitacaoRepository;

    @Autowired
    private DocumentoAnexoRepository documentoAnexoRepository;

    @Autowired
    private LogAuditoriaRepository logAuditoriaRepository;

    @Test
    void salvaEConsultaFluxoPrincipalDaEtapaSeis() {
        Usuario usuario = usuario();
        usuarioRepository.save(usuario);

        Processo processo = processo();
        processo.setUsuarioGerente(usuario);
        processo.cadastrar();
        processoRepository.save(processo);

        Tramitacao tramitacao = tramitacao();
        tramitacao.aplicarAoProcesso(processo);
        tramitacao.registrar();
        tramitacaoRepository.save(tramitacao);

        DocumentoAnexo documento = documento();
        documento.setProcesso(processo);
        documento.setEnviadoPor(usuario);
        documento.anexar();
        documentoAnexoRepository.save(documento);

        LogAuditoria log = new LogAuditoria();
        log.setUsuario(usuario);
        log.setAcao("CADASTRAR");
        log.setEntidadeAfetada("Processo");
        log.setIdEntidadeAfetada(processo.getId());
        log.setDetalhes("Cadastro testado por integracao");
        log.registrar();
        logAuditoriaRepository.save(log);

        assertThat(usuarioRepository.existsByLogin("gabriel")).isTrue();
        assertThat(processoRepository.existsByNumeroProcesso("23005.000001/2026-10")).isTrue();
        assertThat(tramitacaoRepository.findByProcessoId(processo.getId())).hasSize(1);
        assertThat(documentoAnexoRepository.findByProcessoIdAndAtivoTrue(processo.getId())).hasSize(1);
        assertThat(logAuditoriaRepository.findByEntidadeAfetadaAndIdEntidadeAfetada("Processo", processo.getId()))
                .hasSize(1);
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setNome("Gabriel");
        usuario.setEmail("gabriel@sicc.test");
        usuario.setLogin("gabriel");
        usuario.definirSenha("senha123");
        usuario.setPerfil("ADMINISTRADOR");
        return usuario;
    }

    private Processo processo() {
        Processo processo = new Processo();
        processo.setNumeroProcesso("23005.000001/2026-10");
        processo.setTipoInstrumento("CONTRATO");
        processo.setObjeto("Contrato de apoio institucional");
        return processo;
    }

    private Tramitacao tramitacao() {
        Tramitacao tramitacao = new Tramitacao();
        tramitacao.setSetor("DIPAC");
        tramitacao.setResponsavel("Maria");
        tramitacao.setAcaoRealizada("Encaminhado para analise");
        tramitacao.setEtapa("Analise administrativa");
        tramitacao.setStatusNovo("EM_ANALISE");
        return tramitacao;
    }

    private DocumentoAnexo documento() {
        DocumentoAnexo documento = new DocumentoAnexo();
        documento.setNomeArquivo("contrato.pdf");
        documento.setTipoArquivo("application/pdf");
        documento.setCaminhoArquivo("/docs/contrato.pdf");
        return documento;
    }
}
