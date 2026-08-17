package com.moments.sicc.repository;

import com.moments.sicc.domain.VersaoDocumento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VersaoDocumentoRepository extends JpaRepository<VersaoDocumento, Long> {
    List<VersaoDocumento> findByDocumentoIdOrderByVersaoDesc(Long documentoId);
    Optional<VersaoDocumento> findByDocumentoIdAndVersao(Long documentoId, int versao);

    @Query("select coalesce(max(versao.versao), 0) from VersaoDocumento versao "
            + "where versao.documento.id = :documentoId")
    int maiorNumeroPorDocumentoId(@Param("documentoId") Long documentoId);
}
