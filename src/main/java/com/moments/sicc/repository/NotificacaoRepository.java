package com.moments.sicc.repository;

import com.moments.sicc.domain.Notificacao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {
    boolean existsByChaveIdempotencia(String chaveIdempotencia);
    List<Notificacao> findByDestinatarioIdOrderByCriadaEmDesc(Long destinatarioId);
}
