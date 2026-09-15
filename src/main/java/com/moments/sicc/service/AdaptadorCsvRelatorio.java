package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.FormatoRelatorio;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
final class AdaptadorCsvRelatorio implements AdaptadorFormatoRelatorio {
    @Override
    public FormatoRelatorio formato() {
        return FormatoRelatorio.CSV;
    }

    @Override
    public String mime() {
        return "text/csv;charset=UTF-8";
    }

    @Override
    public byte[] codificar(ConteudoRelatorio conteudo) {
        StringBuilder csv = new StringBuilder();
        for (ConteudoRelatorio.Linha linha : conteudo.linhas()) {
            csv.append(linha.celulas().stream()
                    .map(celula -> celula.textual()
                            ? CelulaCsv.textoSeguroParaCsv(celula.valor())
                            : celula.valor())
                    .collect(Collectors.joining(";")))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
