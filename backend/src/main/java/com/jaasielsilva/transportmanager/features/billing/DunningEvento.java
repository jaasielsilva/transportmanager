package com.jaasielsilva.transportmanager.features.billing;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Molde do kit — features/billing/DunningEvento.java
 *
 * Historico da regua E o mecanismo de idempotencia dela: o UNIQUE
 * (empresa_id, etapa, acao) no banco garante que a mesma etapa nunca seja
 * aplicada duas vezes, mesmo com o job rodando em dobro.
 */
@Entity
@Table(name = "dunning_eventos")
public class DunningEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(nullable = false)
    private int etapa;

    @Column(nullable = false, length = 40)
    private String acao;

    @Column(length = 255)
    private String detalhes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long v) { this.empresaId = v; }
    public int getEtapa() { return etapa; }
    public void setEtapa(int v) { this.etapa = v; }
    public String getAcao() { return acao; }
    public void setAcao(String v) { this.acao = v; }
    public String getDetalhes() { return detalhes; }
    public void setDetalhes(String v) { this.detalhes = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
