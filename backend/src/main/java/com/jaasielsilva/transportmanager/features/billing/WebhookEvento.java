package com.jaasielsilva.transportmanager.features.billing;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Molde do kit — features/billing/WebhookEvento.java
 *
 * Registro de todo evento recebido do gateway. O UNIQUE em evento_id e a
 * idempotencia: o gateway reentrega o mesmo evento (timeout, retry, replay) e
 * sem isso uma reentrega vira liberacao ou cobranca duplicada.
 *
 * Guardar o payload permite reprocessar pelo painel um evento que falhou, em
 * vez de corrigir na mao no banco de producao.
 */
@Entity
@Table(name = "webhook_eventos")
public class WebhookEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, length = 120, unique = true)
    private String eventoId;

    @Column(nullable = false, length = 60)
    private String tipo;

    // columnDefinition explicito em vez de @Lob: no Hibernate 6 o @Lob em String
    // vira CLOB e o validate espera tinytext, quebrando a subida contra LONGTEXT.
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "processado_em")
    private LocalDateTime processadoEm;

    @Column(columnDefinition = "LONGTEXT")
    private String erro;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getEventoId() { return eventoId; }
    public void setEventoId(String v) { this.eventoId = v; }
    public String getTipo() { return tipo; }
    public void setTipo(String v) { this.tipo = v; }
    public String getPayload() { return payload; }
    public void setPayload(String v) { this.payload = v; }
    public LocalDateTime getProcessadoEm() { return processadoEm; }
    public void setProcessadoEm(LocalDateTime v) { this.processadoEm = v; }
    public String getErro() { return erro; }
    public void setErro(String v) { this.erro = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
