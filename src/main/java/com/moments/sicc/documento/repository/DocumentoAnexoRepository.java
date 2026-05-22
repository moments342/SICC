package com.moments.sicc.documento.repository;

import com.moments.sicc.documento.domain.DocumentoAnexo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoAnexoRepository extends JpaRepository<DocumentoAnexo, Long> {

    List<DocumentoAnexo> findByProcessoIdAndAtivoTrue(Long processoId);
}
