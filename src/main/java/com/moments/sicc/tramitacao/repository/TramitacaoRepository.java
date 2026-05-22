package com.moments.sicc.tramitacao.repository;

import com.moments.sicc.tramitacao.domain.Tramitacao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TramitacaoRepository extends JpaRepository<Tramitacao, Long> {

    List<Tramitacao> findByProcessoId(Long processoId);
}
