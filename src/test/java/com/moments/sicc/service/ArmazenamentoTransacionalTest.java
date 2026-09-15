package com.moments.sicc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moments.sicc.shared.exception.ArmazenamentoException;
import com.moments.sicc.shared.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-storage-transacional;MODE=PostgreSQL")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ArmazenamentoTransacionalTest {

    @Autowired
    private ArmazenamentoTransacional storage;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ArmazenamentoArquivo arquivos;

    @Test
    void removeArquivoQuandoATransacaoDoBancoVoltaAtras() {
        when(arquivos.armazenar(any(byte[].class), anyString()))
                .thenReturn("documentos/arquivo-rollback");
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
            storage.armazenar(new byte[] {1, 2, 3}, "documentos/1");
            throw new DomainException("falha posterior ao armazenamento");
        })).isInstanceOf(DomainException.class)
                .hasMessage("falha posterior ao armazenamento");

        verify(arquivos).remover("documentos/arquivo-rollback");
    }

    @Test
    void mantemArquivoSomenteQuandoATransacaoDoBancoConfirma() {
        when(arquivos.armazenar(any(byte[].class), anyString()))
                .thenReturn("relatorios/arquivo-confirmado");
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);

        transacao.executeWithoutResult(status ->
                storage.armazenar(new byte[] {4, 5, 6}, "relatorios/consolidado"));

        verify(arquivos, never()).remover(anyString());
    }

    @Test
    void naoPermiteGravacaoSemTransacaoQuePossaCompensar() {
        assertThatThrownBy(() ->
                storage.armazenar(new byte[] {7, 8, 9}, "documentos/2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transação");

        verify(arquivos, never()).armazenar(any(byte[].class), anyString());
        verify(arquivos, never()).remover(anyString());
    }

    @Test
    void mantemAMesmaFixacaoAteOCommitDoBanco() {
        ArquivoTransacionalFalso arquivo = new ArquivoTransacionalFalso();
        ArmazenamentoTransacional armazenamento = new ArmazenamentoTransacional(arquivo);
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);

        transacao.executeWithoutResult(status -> {
            assertThat(armazenamento.armazenar(
                    new byte[] {1, 2, 3}, "documentos/1"))
                    .isEqualTo("documentos/arquivo-fixado");
            assertThat(arquivo.gravacao.validada).isFalse();
            assertThat(arquivo.gravacao.confirmada).isFalse();
            assertThat(arquivo.gravacao.revertida).isFalse();
        });

        assertThat(arquivo.gravacao.validada).isTrue();
        assertThat(arquivo.gravacao.confirmada).isTrue();
        assertThat(arquivo.gravacao.revertida).isFalse();
    }

    @Test
    void mudancaDaRaizAntesDoCommitAbortaERevertePelaMesmaFixacao() {
        ArquivoTransacionalFalso arquivo = new ArquivoTransacionalFalso();
        arquivo.gravacao.falharValidacao = true;
        ArmazenamentoTransacional armazenamento = new ArmazenamentoTransacional(arquivo);
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transacao.executeWithoutResult(status ->
                armazenamento.armazenar(
                        new byte[] {1, 2, 3}, "documentos/1")))
                .isInstanceOf(ArmazenamentoException.class)
                .hasMessageContaining("raiz mudou");

        assertThat(arquivo.gravacao.validada).isTrue();
        assertThat(arquivo.gravacao.confirmada).isFalse();
        assertThat(arquivo.gravacao.revertida).isTrue();
    }

    private static final class ArquivoTransacionalFalso
            implements ArmazenamentoArquivoTransacional {
        private final GravacaoFalsa gravacao = new GravacaoFalsa();

        @Override
        public Gravacao prepararArmazenamento(byte[] conteudo, String prefixo) {
            return gravacao;
        }

        @Override
        public String armazenar(byte[] conteudo, String prefixo) {
            throw new AssertionError("A gravação legada não deve ser usada.");
        }

        @Override
        public Resource carregar(String chave) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remover(String chave) {
            throw new AssertionError("A remoção por uma nova raiz não deve ser usada.");
        }
    }

    private static final class GravacaoFalsa
            implements ArmazenamentoArquivoTransacional.Gravacao {
        private boolean falharValidacao;
        private boolean validada;
        private boolean confirmada;
        private boolean revertida;

        @Override
        public String chave() {
            return "documentos/arquivo-fixado";
        }

        @Override
        public void validarParaCommit() {
            validada = true;
            if (falharValidacao) {
                throw new ArmazenamentoException("a raiz mudou");
            }
        }

        @Override
        public void confirmar() {
            confirmada = true;
        }

        @Override
        public void reverter() {
            revertida = true;
        }
    }
}
