package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.FormatoRelatorio;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class GeradorArquivoRelatorio {
    private final Map<FormatoRelatorio, AdaptadorFormatoRelatorio> adaptadores;

    GeradorArquivoRelatorio(List<AdaptadorFormatoRelatorio> adaptadores) {
        EnumMap<FormatoRelatorio, AdaptadorFormatoRelatorio> catalogo =
                new EnumMap<>(FormatoRelatorio.class);
        for (AdaptadorFormatoRelatorio adaptador : adaptadores) {
            if (catalogo.put(adaptador.formato(), adaptador) != null) {
                throw new IllegalStateException(
                        "Há mais de um adapter para o formato " + adaptador.formato() + ".");
            }
        }
        if (catalogo.size() != FormatoRelatorio.values().length) {
            throw new IllegalStateException("O catálogo não cobre todos os formatos de relatório.");
        }
        this.adaptadores = Collections.unmodifiableMap(catalogo);
    }

    byte[] gerar(FormatoRelatorio formato, ConteudoRelatorio conteudo) {
        return adaptador(formato).codificar(Objects.requireNonNull(conteudo));
    }

    String mime(FormatoRelatorio formato) {
        return adaptador(formato).mime();
    }

    private AdaptadorFormatoRelatorio adaptador(FormatoRelatorio formato) {
        return Objects.requireNonNull(
                adaptadores.get(Objects.requireNonNull(formato)),
                "Formato de relatório sem adapter.");
    }
}
