package com.moments.sicc.repository;

import com.moments.sicc.domain.RelatorioGerado;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelatorioGeradoRepository extends JpaRepository<RelatorioGerado, Long> {
    List<RelatorioGerado> findAllByOrderByCriadoEmDesc();
}
