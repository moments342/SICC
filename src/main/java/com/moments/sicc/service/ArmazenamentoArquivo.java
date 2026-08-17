package com.moments.sicc.service;

import org.springframework.core.io.Resource;

public interface ArmazenamentoArquivo {
    String armazenar(byte[] conteudo, String prefixo);

    Resource carregar(String chave);
}
