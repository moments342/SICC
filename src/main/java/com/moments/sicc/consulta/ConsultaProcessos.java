package com.moments.sicc.consulta;

import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.service.RegrasDeVigencia.CondicaoDeData;
import com.moments.sicc.service.RegrasDeVigencia.ReferenciaDeVigencia;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ConsultaProcessos {
    private static final String TIPO_NAO_FORMALIZADO = "Ainda não formalizado";

    private final ProcessoAdministrativoRepository processos;

    public Page<ProcessoAdministrativo> interna(
            Filtros filtros,
            Pageable pageable,
            ReferenciaDeVigencia vigencia,
            boolean incluirInativos) {
        return processos.findAll(
                especificacao(filtros, Escopo.INTERNO, vigencia, incluirInativos),
                pageable);
    }

    public Page<ProcessoAdministrativo> publica(
            Filtros filtros, Pageable pageable, ReferenciaDeVigencia vigencia) {
        return processos.findAll(especificacao(filtros, Escopo.PUBLICO, vigencia, true), pageable);
    }

    public Page<ProcessoAdministrativo> interna(
            Filtros filtros, Pageable pageable, ReferenciaDeVigencia vigencia,
            boolean incluirInativos, String ordenacao, Long responsavelId, boolean semResponsavel) {
        Specification<ProcessoAdministrativo> consulta = especificacao(
                filtros, Escopo.INTERNO, vigencia, incluirInativos);
        return processos.findAll(consulta.and((raiz, query, criteria) -> {
            List<Predicate> responsavel = new ArrayList<>();
            if (semResponsavel) responsavel.add(criteria.isNull(raiz.get("responsavel")));
            if (responsavelId != null) responsavel.add(criteria.equal(raiz.get("responsavel").get("id"), responsavelId));
            // Sorting belongs to the complete query, before pagination. Missing dates go last.
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                if ("VIGENCIA_CONTRATUAL".equals(ordenacao) || "VIGENCIA_TED".equals(ordenacao)) {
                    Path<LocalDate> data = raiz.join("instrumento", JoinType.LEFT).get(
                            "VIGENCIA_TED".equals(ordenacao) ? "vigenciaTedFinal" : "vigenciaContratualFinal");
                    query.orderBy(criteria.asc(criteria.<Integer>selectCase().when(criteria.isNull(data), 1).otherwise(0)),
                            criteria.asc(data), criteria.asc(raiz.get("id")));
                } else query.orderBy(criteria.asc(raiz.get("id")));
            }
            return criteria.and(responsavel.toArray(Predicate[]::new));
        }), pageable);
    }

    private Specification<ProcessoAdministrativo> especificacao(
            Filtros filtros,
            Escopo escopo,
            ReferenciaDeVigencia vigencia,
            boolean incluirInativos) {
        return (raiz, query, criteria) -> {
            Join<ProcessoAdministrativo, InstrumentoContratual> instrumento =
                    raiz.join("instrumento", JoinType.LEFT);
            List<Predicate> predicados = new ArrayList<>();

            if (escopo == Escopo.INTERNO && !incluirInativos) {
                predicados.add(criteria.isTrue(raiz.get("ativo")));
            }
            adicionarContem(predicados, criteria, raiz.get("numero"), filtros.numero());
            adicionarContem(predicados, criteria, raiz.get("origem"), filtros.origem());
            adicionarContem(predicados, criteria, instrumento.get("objeto"), filtros.objeto());
            adicionarContem(
                    predicados,
                    criteria,
                    instrumento.get("coordenador"),
                    filtros.coordenador());
            adicionarTipo(predicados, criteria, instrumento, filtros.tipo(), escopo);
            adicionarStatus(predicados, criteria, instrumento, filtros.status(), vigencia);
            adicionarVigencia(
                    predicados,
                    criteria,
                    instrumento,
                    filtros.vigencia(),
                    escopo,
                    vigencia);
            if (escopo == Escopo.INTERNO) {
                adicionarVigenciaIndividual(predicados, criteria, instrumento,
                        "vigenciaContratualFinal", filtros.vigenciaContratual(), vigencia);
                adicionarVigenciaIndividual(predicados, criteria, instrumento,
                        "vigenciaTedFinal", filtros.vigenciaTed(), vigencia);
            }

            return criteria.and(predicados.toArray(Predicate[]::new));
        };
    }

    private void adicionarContem(
            List<Predicate> predicados,
            CriteriaBuilder criteria,
            Path<String> campo,
            String filtro) {
        if (!StringUtils.hasText(filtro)) return;
        String termo = escaparLike(filtro.trim().toLowerCase(Locale.ROOT));
        predicados.add(criteria.like(criteria.lower(campo), "%" + termo + "%", '\\'));
    }

    private void adicionarTipo(
            List<Predicate> predicados,
            CriteriaBuilder criteria,
            Join<ProcessoAdministrativo, InstrumentoContratual> instrumento,
            String filtro,
            Escopo escopo) {
        if (!StringUtils.hasText(filtro)) return;
        if (escopo == Escopo.PUBLICO && TIPO_NAO_FORMALIZADO.equals(filtro.trim())) {
            predicados.add(criteria.isNull(instrumento.get("id")));
            return;
        }
        TipoInstrumento tipo = enumOuNulo(filtro, TipoInstrumento.class);
        predicados.add(tipo == null
                ? criteria.disjunction()
                : criteria.equal(instrumento.get("tipo"), tipo));
    }

    private void adicionarStatus(
            List<Predicate> predicados,
            CriteriaBuilder criteria,
            Join<ProcessoAdministrativo, InstrumentoContratual> instrumento,
            String filtro,
            ReferenciaDeVigencia vigencia) {
        if (!StringUtils.hasText(filtro)) return;
        StatusProcesso status = enumOuNulo(filtro, StatusProcesso.class);
        if (status == null) {
            predicados.add(criteria.disjunction());
            return;
        }
        Path<LocalDate> vigenciaContratual = instrumento.get("vigenciaContratualFinal");
        predicados.add(condicao(criteria, vigenciaContratual, vigencia.condicao(status)));
    }

    private void adicionarVigencia(
            List<Predicate> predicados,
            CriteriaBuilder criteria,
            Join<ProcessoAdministrativo, InstrumentoContratual> instrumento,
            String filtro,
            Escopo escopo,
            ReferenciaDeVigencia vigencia) {
        if (!StringUtils.hasText(filtro)) return;
        SituacaoVigencia situacao = enumOuNulo(filtro, SituacaoVigencia.class);
        if (situacao == null) {
            predicados.add(criteria.disjunction());
            return;
        }
        Predicate corresponde = criteria.or(
                condicao(
                        criteria,
                        instrumento.get("vigenciaContratualFinal"),
                        vigencia.condicao(situacao)),
                condicao(
                        criteria,
                        instrumento.get("vigenciaTedFinal"),
                        vigencia.condicao(situacao)));
        predicados.add(escopo == Escopo.INTERNO
                ? criteria.and(criteria.isNotNull(instrumento.get("id")), corresponde)
                : corresponde);
    }

    private void adicionarVigenciaIndividual(
            List<Predicate> predicados, CriteriaBuilder criteria,
            Join<ProcessoAdministrativo, InstrumentoContratual> instrumento,
            String campo, String filtro, ReferenciaDeVigencia vigencia) {
        if (!StringUtils.hasText(filtro)) return;
        SituacaoVigencia situacao = enumOuNulo(filtro, SituacaoVigencia.class);
        predicados.add(situacao == null ? criteria.disjunction()
                : criteria.and(criteria.isNotNull(instrumento.get("id")),
                        condicao(criteria, instrumento.get(campo), vigencia.condicao(situacao))));
    }

    private Predicate condicao(
            CriteriaBuilder criteria,
            Path<LocalDate> dataFinal,
            CondicaoDeData condicao) {
        if (condicao.somenteAusente()) return criteria.isNull(dataFinal);
        List<Predicate> limites = new ArrayList<>(2);
        if (condicao.inicioInclusivo() != null) {
            limites.add(criteria.greaterThanOrEqualTo(
                    dataFinal, condicao.inicioInclusivo()));
        }
        if (condicao.fimExclusivo() != null) {
            limites.add(criteria.lessThan(dataFinal, condicao.fimExclusivo()));
        }
        return criteria.and(limites.toArray(Predicate[]::new));
    }

    private <E extends Enum<E>> E enumOuNulo(String valor, Class<E> tipo) {
        try {
            return Enum.valueOf(tipo, valor.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String escaparLike(String valor) {
        return valor.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public record Filtros(
            String numero,
            String origem,
            String tipo,
            String status,
            String vigencia,
            String objeto,
            String coordenador,
            String vigenciaContratual,
            String vigenciaTed) {
        public Filtros(String numero, String origem, String tipo, String status,
                String vigencia, String objeto, String coordenador) {
            this(numero, origem, tipo, status, vigencia, objeto, coordenador, null, null);
        }
    }

    private enum Escopo {
        INTERNO,
        PUBLICO
    }
}
