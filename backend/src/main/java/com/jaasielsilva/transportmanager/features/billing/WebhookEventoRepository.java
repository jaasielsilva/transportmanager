package com.jaasielsilva.transportmanager.features.billing;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventoRepository extends JpaRepository<WebhookEvento, Long> {

    Optional<WebhookEvento> findByEventoId(String eventoId);

    @Modifying
    @Query("""
            UPDATE WebhookEvento w SET w.processadoEm = CURRENT_TIMESTAMP, w.erro = NULL
             WHERE w.eventoId = :eventoId
            """)
    void marcarProcessado(@Param("eventoId") String eventoId);

    @Modifying
    @Query("UPDATE WebhookEvento w SET w.erro = :erro WHERE w.eventoId = :eventoId")
    void marcarErro(@Param("eventoId") String eventoId, @Param("erro") String erro);
}
