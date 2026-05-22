package com.moments.sicc.processo.repository;

import com.moments.sicc.processo.domain.Processo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessoRepository extends JpaRepository<Processo, Long> {

    boolean existsByNumeroProcesso(String numeroProcesso);

    List<Processo> findByStatusAtual(String statusAtual);
}
