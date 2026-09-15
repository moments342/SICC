package com.moments.sicc.service;

import static com.moments.sicc.support.ArquivoDocumentoTeste.pdfValido;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moments.sicc.api.ApiDtos.CriarDocumentoRequest;
import com.moments.sicc.api.ApiDtos.GerarRelatorioRequest;
import com.moments.sicc.domain.Enums.CategoriaDocumento;
import com.moments.sicc.domain.Enums.FormatoRelatorio;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.domain.Enums.TipoRelatorio;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.DocumentoRepository;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.repository.RegistroAuditoriaRepository;
import com.moments.sicc.repository.RelatorioGeradoRepository;
import com.moments.sicc.repository.UsuarioInternoRepository;
import com.moments.sicc.repository.VersaoDocumentoRepository;
import com.moments.sicc.shared.exception.ArmazenamentoException;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-storage-rollback;MODE=PostgreSQL")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ArmazenamentoRollbackIntegrationTest {

    @Autowired
    private DocumentoService documentosService;
    @Autowired
    private RelatorioDashboardService relatoriosService;
    @Autowired
    private DocumentoRepository documentos;
    @Autowired
    private VersaoDocumentoRepository versoes;
    @Autowired
    private RelatorioGeradoRepository relatorios;
    @Autowired
    private RegistroAuditoriaRepository auditoria;
    @Autowired
    private UsuarioInternoRepository usuarios;
    @Autowired
    private ProcessoAdministrativoRepository processos;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ArmazenamentoArquivo arquivos;

    private UsuarioInterno autor;
    private ProcessoAdministrativo processo;

    @BeforeEach
    void prepararContexto() {
        autor = usuarios.findByLoginIgnoreCase("admin").orElseThrow();
        ProcessoAdministrativo novo = new ProcessoAdministrativo();
        novo.setNumero("PROC-STORAGE-ROLLBACK-018");
        novo.setOrigem("DIPAC");
        novo.setDataCadastro(LocalDate.of(2026, 8, 30));
        processo = processos.saveAndFlush(novo);
    }

    @Test
    void rollbackDeDocumentoRemoveArquivoEMetadadosEAuditoriaDeSucesso() {
        when(arquivos.armazenar(any(byte[].class), anyString()))
                .thenReturn("documentos/arquivo-rollback-servico");
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo",
                "administrativo.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfValido("documento que sofrerá rollback"));

        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
            documentosService.criar(
                    new CriarDocumentoRequest(
                            ProprietarioDocumento.PROCESSO,
                            processo.getId(),
                            CategoriaDocumento.ADMINISTRATIVO,
                            "Documento com rollback"),
                    arquivo,
                    autor,
                    "127.0.0.1");
            throw new FalhaSentinela();
        })).isInstanceOf(FalhaSentinela.class);

        verify(arquivos).remover("documentos/arquivo-rollback-servico");
        assertThat(documentos.findAll()).isEmpty();
        assertThat(versoes.findAll()).isEmpty();
        assertThat(auditoria.findAll())
                .noneMatch(registro -> "CRIAR_DOCUMENTO".equals(registro.getAcao())
                        && registro.isSucesso());
    }

    @Test
    void rollbackDeRelatorioCompensaArquivoEAuditoriaSemOcultarFalhaOriginal() {
        String chave = "relatorios/arquivo-rollback-servico";
        when(arquivos.armazenar(any(byte[].class), anyString())).thenReturn(chave);
        doThrow(new ArmazenamentoException("falha controlada na compensação"))
                .when(arquivos).remover(chave);
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
            relatoriosService.gerar(
                    new GerarRelatorioRequest(
                            TipoRelatorio.CONSOLIDADO, FormatoRelatorio.CSV, Map.of()),
                    autor,
                    "127.0.0.1");
            throw new FalhaSentinela();
        })).isInstanceOf(FalhaSentinela.class);

        verify(arquivos).remover(chave);
        assertThat(relatorios.findAll()).isEmpty();
        assertThat(auditoria.findAll())
                .noneMatch(registro -> "GERAR_RELATORIO".equals(registro.getAcao())
                        && registro.isSucesso());
    }

    private static final class FalhaSentinela extends RuntimeException {}
}
