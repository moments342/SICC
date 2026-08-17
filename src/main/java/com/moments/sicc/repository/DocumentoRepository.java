package com.moments.sicc.repository;

import com.moments.sicc.domain.Documento;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    List<Documento> findByProprietarioTipoAndProprietarioIdAndAtivoTrue(
            ProprietarioDocumento proprietarioTipo, Long proprietarioId);
}
