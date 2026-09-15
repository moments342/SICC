package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.FormatoRelatorio;

interface AdaptadorFormatoRelatorio {
    FormatoRelatorio formato();

    String mime();

    byte[] codificar(ConteudoRelatorio conteudo);
}
