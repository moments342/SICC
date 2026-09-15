package com.moments.sicc.repository;

import com.moments.sicc.domain.AlteracaoContratual;
import com.moments.sicc.domain.Enums.TipoAlteracao;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlteracaoContratualRepository extends JpaRepository<AlteracaoContratual, Long> {
    interface HierarquiaAlteracao {
        Long getAlteracaoId();
        Long getInstrumentoId();
        Long getProcessoId();
        TipoAlteracao getTipo();
    }

    List<AlteracaoContratual> findByInstrumentoIdOrderByDataEfetivacaoAscOrdemOficialAsc(Long instrumentoId);
    boolean existsByDocumentoAssinadoId(Long documentoId);

    @Query("""
            select a.id as alteracaoId,
                   a.instrumento.id as instrumentoId,
                   a.instrumento.processo.id as processoId,
                   a.tipo as tipo
            from AlteracaoContratual a
            where a.id = :id
            """)
    Optional<HierarquiaAlteracao> findHierarquiaById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AlteracaoContratual a where a.id = :id")
    Optional<AlteracaoContratual> findByIdForUpdate(@Param("id") Long id);
}
