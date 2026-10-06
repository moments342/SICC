package com.moments.sicc.consulta;

import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.shared.PaginacaoSegura;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Projeções limitadas: nenhum histórico, documento ou entidade completa no seletor. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaInstrumentosAlteracao {
    private final EntityManager entityManager;
    private static final String ELEGIVEIS = """
            from InstrumentoContratual i join i.processo p
            where p.ativo = true
            """;

    public Page<Opcao> consultar(String busca, int pagina, int tamanho) {
        var pageable = PaginacaoSegura.criar(pagina, tamanho, Sort.unsorted());
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new DomainException("A página solicitada excede o limite da consulta.");
        }
        String termo = busca == null ? "" : busca.trim().toLowerCase(Locale.ROOT);
        String filtro = termo.isEmpty() ? "" : """
                and (lower(i.numero) like :busca escape '\\'
                     or lower(p.numero) like :busca escape '\\'
                     or lower(p.origem) like :busca escape '\\')
                """;
        var itens = entityManager.createQuery("""
                select new com.moments.sicc.consulta.ConsultaInstrumentosAlteracao$Opcao(
                    i.id, i.numero, i.tipo, p.numero)
                """ + ELEGIVEIS + filtro + " order by i.numero, i.id", Opcao.class);
        var total = entityManager.createQuery("select count(i.id) " + ELEGIVEIS + filtro, Long.class);
        if (!termo.isEmpty()) {
            String literal = "%" + termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            itens.setParameter("busca", literal);
            total.setParameter("busca", literal);
        }
        return new PageImpl<>(itens.setFirstResult((int) pageable.getOffset())
                .setMaxResults(tamanho).getResultList(), pageable, total.getSingleResult());
    }

    public Detalhe buscar(Long id) {
        // A mesma elegibilidade se aplica à seleção restaurada da sessão.
        return entityManager.createQuery("""
                select new com.moments.sicc.consulta.ConsultaInstrumentosAlteracao$Linha(
                    i.id, i.numero, i.tipo, p.numero, i.objeto, i.descricao, i.natureza,
                    i.coordenador, i.participes, i.valorAtual, i.vigenciaContratualFinal, i.vigenciaTedFinal)
                """ + ELEGIVEIS + " and i.id = :id", Linha.class)
                .setParameter("id", id).getResultStream().findFirst()
                .map(l -> new Detalhe(l.id(), l.numero(), l.tipo(), l.numeroProcesso(), l.objeto(),
                        l.descricao(), l.natureza(), l.coordenador(), List.of(l.participes().split("\\n")),
                        l.valorAtual(), l.vigenciaContratualFinal(), l.vigenciaTedFinal()))
                .orElseThrow(() -> new NotFoundException("Instrumento Contratual não encontrado ou Processo Administrativo inativo."));
    }

    public record Opcao(Long id, String numero, TipoInstrumento tipo, String numeroProcesso) { }
    public record Detalhe(Long id, String numero, TipoInstrumento tipo, String numeroProcesso,
            String objeto, String descricao, String natureza, String coordenador, List<String> participes,
            BigDecimal valorAtual, LocalDate vigenciaContratualFinal, LocalDate vigenciaTedFinal) { }
    private record Linha(Long id, String numero, TipoInstrumento tipo, String numeroProcesso,
            String objeto, String descricao, String natureza, String coordenador, String participes,
            BigDecimal valorAtual, LocalDate vigenciaContratualFinal, LocalDate vigenciaTedFinal) { }
}
