package com.moments.sicc.repository;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Movimentacao;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {
    Optional<Movimentacao> findFirstByContextoTipoAndContextoIdAndDataMovimentacaoLessThanEqualOrderByDataMovimentacaoDescSequenciaDiariaDesc(
            ContextoTramitacao contextoTipo, Long contextoId, LocalDate dataMovimentacao);
    List<Movimentacao> findByContextoTipoAndContextoIdOrderByDataMovimentacaoAscSequenciaDiariaAsc(
            ContextoTramitacao contextoTipo, Long contextoId);
    Optional<Movimentacao> findFirstByContextoTipoAndContextoIdOrderByDataMovimentacaoDescSequenciaDiariaDesc(
            ContextoTramitacao contextoTipo, Long contextoId);

    @Query("""
            select movimentacao
            from Movimentacao movimentacao
            join fetch movimentacao.setorDestino
            where movimentacao.contextoTipo = :contextoTipo
              and movimentacao.contextoId in :contextoIds
              and not exists (
                    select posterior.id
                    from Movimentacao posterior
                    where posterior.contextoTipo = movimentacao.contextoTipo
                      and posterior.contextoId = movimentacao.contextoId
                      and (
                            posterior.dataMovimentacao > movimentacao.dataMovimentacao
                            or (
                                posterior.dataMovimentacao = movimentacao.dataMovimentacao
                                and posterior.sequenciaDiaria > movimentacao.sequenciaDiaria
                            )
                      )
              )
            """)
    List<Movimentacao> findUltimasPorContextos(
            @Param("contextoTipo") ContextoTramitacao contextoTipo,
            @Param("contextoIds") List<Long> contextoIds);
}
