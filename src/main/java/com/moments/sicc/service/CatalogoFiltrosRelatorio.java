package com.moments.sicc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.domain.Enums.TipoRelatorio;
import com.moments.sicc.shared.exception.DomainException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogoFiltrosRelatorio {
    private final ObjectMapper objectMapper;
    private final GeracaoRelatorios relatorios;

    public Map<String, String> normalizar(
            TipoRelatorio tipo, Map<String, String> filtros) {
        if (filtros == null || filtros.isEmpty()) return Map.of();
        Map<String, String> normalizados = new TreeMap<>();
        filtros.forEach((nome, valor) -> {
            if (nome != null && !nome.isBlank() && valor != null && !valor.isBlank()) {
                normalizados.put(nome.trim(), valor.trim());
            }
        });
        Set<String> permitidos = relatorios.filtrosPermitidos(tipo);
        List<String> naoAplicaveis = normalizados.keySet().stream()
                .filter(nome -> !permitidos.contains(nome))
                .sorted()
                .toList();
        if (!naoAplicaveis.isEmpty()) {
            throw new DomainException("Filtros não aplicáveis a " + tipo + ": "
                    + String.join(", ", naoAplicaveis) + ".");
        }
        validarValores(normalizados);
        return Collections.unmodifiableMap(normalizados);
    }

    public String serializar(Map<String, String> filtros) {
        try {
            return objectMapper.writeValueAsString(
                    filtros == null ? Map.of() : new TreeMap<>(filtros));
        } catch (Exception e) {
            throw new IllegalArgumentException("Filtros de relatório inválidos.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> desserializar(String filtros) {
        if (filtros == null || filtros.isBlank()) return Map.of();
        try {
            return Collections.unmodifiableMap(
                    new TreeMap<>(objectMapper.readValue(filtros, Map.class)));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void validarValores(Map<String, String> filtros) {
        try {
            if (filtros.containsKey("ano")) Integer.parseInt(filtros.get("ano"));
            if (filtros.containsKey("tipo")) TipoInstrumento.valueOf(filtros.get("tipo"));
            if (filtros.containsKey("status")) StatusProcesso.valueOf(filtros.get("status"));
            if (filtros.containsKey("vigenciaContratual")) {
                SituacaoVigencia.valueOf(filtros.get("vigenciaContratual"));
            }
            if (filtros.containsKey("vigenciaTed")) {
                SituacaoVigencia.valueOf(filtros.get("vigenciaTed"));
            }
            if (filtros.containsKey("contexto")) {
                ContextoTramitacao.valueOf(filtros.get("contexto"));
            }
            LocalDate inicio = filtros.containsKey("dataInicial")
                    ? LocalDate.parse(filtros.get("dataInicial")) : null;
            LocalDate fim = filtros.containsKey("dataFinal")
                    ? LocalDate.parse(filtros.get("dataFinal")) : null;
            if (inicio != null && fim != null && inicio.isAfter(fim)) {
                throw new DomainException(
                        "O filtro dataInicial não pode ser posterior a dataFinal.");
            }
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DomainException("Valor de filtro de relatório inválido.");
        }
    }
}
