package com.moments.sicc.repository;

import com.moments.sicc.domain.AlteracaoCampo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlteracaoCampoRepository extends JpaRepository<AlteracaoCampo, Long> {
    List<AlteracaoCampo> findByAlteracaoId(Long alteracaoId);
}
