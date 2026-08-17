package com.moments.sicc.repository;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Movimentacao;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {
    Optional<Movimentacao> findFirstByContextoTipoAndContextoIdAndDataMovimentacaoOrderBySequenciaDiariaDesc(
            ContextoTramitacao contextoTipo, Long contextoId, LocalDate dataMovimentacao);
    List<Movimentacao> findByContextoTipoAndContextoIdOrderByDataMovimentacaoAscSequenciaDiariaAsc(
            ContextoTramitacao contextoTipo, Long contextoId);
    Optional<Movimentacao> findFirstByContextoTipoAndContextoIdOrderByDataMovimentacaoDescSequenciaDiariaDesc(
            ContextoTramitacao contextoTipo, Long contextoId);
}
