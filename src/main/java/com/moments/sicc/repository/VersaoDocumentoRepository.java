package com.moments.sicc.repository;

import com.moments.sicc.domain.VersaoDocumento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersaoDocumentoRepository extends JpaRepository<VersaoDocumento, Long> {
    int countByDocumentoId(Long documentoId);
    List<VersaoDocumento> findByDocumentoIdOrderByVersaoDesc(Long documentoId);
    Optional<VersaoDocumento> findByDocumentoIdAndVersao(Long documentoId, int versao);
}
