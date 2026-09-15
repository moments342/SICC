package com.moments.sicc.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class ArmazenamentoTransacional {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArmazenamentoTransacional.class);

    private final ArmazenamentoArquivo arquivos;

    public String armazenar(byte[] conteudo, String prefixo) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "O armazenamento persistente exige uma transação ativa.");
        }
        if (arquivos instanceof ArmazenamentoArquivoTransacional transacional) {
            return armazenarComRaizFixada(transacional, conteudo, prefixo);
        }
        return armazenarComCompensacaoLegada(conteudo, prefixo);
    }

    private String armazenarComRaizFixada(
            ArmazenamentoArquivoTransacional transacional,
            byte[] conteudo,
            String prefixo) {
        ArmazenamentoArquivoTransacional.Gravacao gravacao =
                transacional.prepararArmazenamento(conteudo, prefixo);
        try {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void beforeCommit(boolean readOnly) {
                            gravacao.validarParaCommit();
                        }

                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_COMMITTED) confirmar(gravacao);
                            else reverter(gravacao);
                        }
                    });
        } catch (RuntimeException falha) {
            reverterPreservandoFalha(gravacao, falha);
            throw falha;
        }
        return gravacao.chave();
    }

    private String armazenarComCompensacaoLegada(byte[] conteudo, String prefixo) {
        String chave = arquivos.armazenar(conteudo, prefixo);
        try {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) removerDepoisDoRollback(chave);
                        }
                    });
        } catch (RuntimeException falha) {
            removerPreservandoFalha(chave, falha);
            throw falha;
        }
        return chave;
    }

    public Resource carregar(String chave) {
        return arquivos.carregar(chave);
    }

    private void removerDepoisDoRollback(String chave) {
        try {
            arquivos.remover(chave);
        } catch (RuntimeException falha) {
            LOGGER.error("Não foi possível compensar o arquivo {} após rollback.", chave, falha);
        }
    }

    private void removerPreservandoFalha(String chave, RuntimeException falhaPrincipal) {
        try {
            arquivos.remover(chave);
        } catch (RuntimeException falhaDeRemocao) {
            falhaPrincipal.addSuppressed(falhaDeRemocao);
        }
    }

    private void confirmar(ArmazenamentoArquivoTransacional.Gravacao gravacao) {
        try {
            gravacao.confirmar();
        } catch (RuntimeException falha) {
            LOGGER.error(
                    "Não foi possível liberar a fixação do arquivo {} após commit.",
                    gravacao.chave(),
                    falha);
        }
    }

    private void reverter(ArmazenamentoArquivoTransacional.Gravacao gravacao) {
        try {
            gravacao.reverter();
        } catch (RuntimeException falha) {
            LOGGER.error(
                    "Não foi possível compensar o arquivo {} após rollback.",
                    gravacao.chave(),
                    falha);
        }
    }

    private void reverterPreservandoFalha(
            ArmazenamentoArquivoTransacional.Gravacao gravacao,
            RuntimeException falhaPrincipal) {
        try {
            gravacao.reverter();
        } catch (RuntimeException falhaDeRemocao) {
            falhaPrincipal.addSuppressed(falhaDeRemocao);
        }
    }
}
