package com.moments.sicc.consulta;

import com.moments.sicc.domain.AlteracaoContratual;
import com.moments.sicc.domain.Enums.*;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.service.RegrasDeVigencia;
import com.moments.sicc.shared.PaginacaoSegura;
import com.moments.sicc.shared.exception.DomainException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Consulta interna e limitada da hierarquia de Documento Anexo, sem carregar entidades completas. */
@Component
@RequiredArgsConstructor
public class ConsultaProprietariosDocumento {
    private final EntityManager entityManager;
    private final RegrasDeVigencia vigencia;

    @Transactional(readOnly = true)
    public Page<Proprietario> consultar(ProprietarioDocumento tipo, String busca,
            boolean incluirInativos, int pagina, int tamanho) {
        var pageable = PaginacaoSegura.criar(pagina, tamanho, Sort.unsorted());
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new DomainException("A página solicitada excede o limite da consulta.");
        }
        var referencia = vigencia.referenciaAtual();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Linha.class);
        var caminhos = caminhos(query, tipo);
        query.select(cb.construct(Linha.class,
                caminhos.raiz().get("id"), caminhos.numero(),
                caminhos.processo().get("numero"),
                caminhos.processo().get("origem"),
                caminhos.processo().get("ativo"),
                caminhos.instrumento().get("numero"),
                caminhos.instrumento().get("tipo"),
                caminhos.instrumento().get("vigenciaContratualFinal"),
                (caminhos.tipoAlteracao() != null ? caminhos.raiz().get("estado")
                        : cb.nullLiteral(EstadoAlteracao.class))));
        query.where(filtro(cb, caminhos, busca, incluirInativos));
        query.orderBy(cb.asc(caminhos.numero()), cb.asc(caminhos.raiz().get("id")));
        List<Proprietario> itens = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset()).setMaxResults(tamanho).getResultList()
                .stream().map(linha -> new Proprietario(
                        linha.id(), linha.numero(),
                        linha.numeroProcesso(), linha.origem(),
                        linha.numeroInstrumento(), linha.tipoInstrumento(),
                        linha.estadoAlteracao(),
                        referencia.status(linha.vigencia()),
                        linha.processoAtivo())).toList();
        var count = cb.createQuery(Long.class);
        var contagem = caminhos(count, tipo);
        count.select(cb.count(contagem.raiz()));
        count.where(filtro(cb, contagem, busca, incluirInativos));
        return new PageImpl<>(itens, pageable, entityManager.createQuery(count).getSingleResult());
    }

    private Caminhos caminhos(CriteriaQuery<?> query, ProprietarioDocumento tipo) {
        return switch (tipo) {
            case PROCESSO -> {
                var processo = query.from(ProcessoAdministrativo.class);
                yield new Caminhos(processo, processo, processo.join("instrumento", JoinType.LEFT),
                        processo.get("numero"), null);
            }
            case INSTRUMENTO -> {
                var instrumento = query.from(InstrumentoContratual.class);
                yield new Caminhos(instrumento, instrumento.join("processo"), instrumento,
                        instrumento.get("numero"), null);
            }
            case TERMO_ADITIVO, APOSTILAMENTO -> {
                var alteracao = query.from(AlteracaoContratual.class);
                var instrumento = alteracao.join("instrumento");
                yield new Caminhos(alteracao, instrumento.join("processo"), instrumento,
                        alteracao.get("numeroOficial"), Arrays.stream(TipoAlteracao.values())
                                .filter(valor -> valor.proprietarioDocumento() == tipo).findFirst().orElseThrow());
            }
        };
    }

    private Predicate filtro(CriteriaBuilder cb, Caminhos caminhos,
            String busca, boolean incluirInativos) {
        List<Predicate> filtros = new ArrayList<>();
        if (!incluirInativos) filtros.add(cb.isTrue(caminhos.processo().get("ativo")));
        if (caminhos.tipoAlteracao() != null) {
            filtros.add(cb.equal(caminhos.raiz().get("tipo"), caminhos.tipoAlteracao()));
        }
        if (busca != null && !busca.isBlank()) {
            String termo = "%" + busca.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filtros.add(cb.or(
                    cb.like(cb.lower(caminhos.numero()), termo, '\\'),
                    cb.like(cb.lower(caminhos.processo().get("numero")), termo, '\\'),
                    cb.like(cb.lower(caminhos.processo().get("origem")), termo, '\\'),
                    cb.like(cb.lower(caminhos.instrumento().get("numero")), termo, '\\')));
        }
        return cb.and(filtros.toArray(Predicate[]::new));
    }

    private record Linha(Long id, String numero, String numeroProcesso, String origem,
            Boolean processoAtivo, String numeroInstrumento, TipoInstrumento tipoInstrumento,
            LocalDate vigencia, EstadoAlteracao estadoAlteracao) { }

    private record Caminhos(From<?, ?> raiz, From<?, ?> processo, From<?, ?> instrumento,
            Path<String> numero, TipoAlteracao tipoAlteracao) { }

    public record Proprietario(Long id, String numero, String numeroProcesso, String origem,
            String numeroInstrumento, TipoInstrumento tipoInstrumento, EstadoAlteracao estadoAlteracao,
            StatusProcesso statusProcesso, boolean processoAtivo) { }
}
