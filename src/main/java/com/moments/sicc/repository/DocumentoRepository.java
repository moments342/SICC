package com.moments.sicc.repository;

import com.moments.sicc.domain.Documento;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    interface HierarquiaDocumento {
        Long getDocumentoId();
        ProprietarioDocumento getProprietarioTipo();
        Long getProprietarioId();
    }

    List<Documento> findByProprietarioTipoAndProprietarioIdAndAtivoTrue(
            ProprietarioDocumento proprietarioTipo, Long proprietarioId);

    List<Documento> findByProprietarioTipoAndProprietarioId(
            ProprietarioDocumento proprietarioTipo, Long proprietarioId);

    @Query("""
            select documento.id as documentoId,
                   documento.proprietarioTipo as proprietarioTipo,
                   documento.proprietarioId as proprietarioId
            from Documento documento
            where documento.id = :id
            """)
    Optional<HierarquiaDocumento> findHierarquiaById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select documento from Documento documento where documento.id = :id")
    Optional<Documento> findByIdComBloqueio(@Param("id") Long id);
}
