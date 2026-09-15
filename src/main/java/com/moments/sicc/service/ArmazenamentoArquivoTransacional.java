package com.moments.sicc.service;

/**
 * Extensão usada quando o armazenamento consegue manter a mesma raiz fixada
 * durante toda a transação do banco de dados.
 */
interface ArmazenamentoArquivoTransacional extends ArmazenamentoArquivo {
    Gravacao prepararArmazenamento(byte[] conteudo, String prefixo);

    interface Gravacao {
        String chave();

        void validarParaCommit();

        void confirmar();

        void reverter();
    }
}
