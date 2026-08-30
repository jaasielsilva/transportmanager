package com.jaasielsilva.transportmanager.features.rastreamento.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

/**
 * Log append-only de posicoes de GPS enviadas pelo motorista (polling do
 * navegador). Nao estende BaseEntity de proposito: BaseEntity tras
 * updatedAt, e um evento gravado nunca e atualizado.
 */
@Entity
@Table(name = "posicoes_gps")
public class PosicaoGps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "carga_id", nullable = false)
    private Long cargaId;

    @Column(name = "motorista_id", nullable = false)
    private Long motoristaId;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "registrado_em", nullable = false)
    private LocalDateTime registradoEm;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long v) { this.empresaId = v; }
    public Long getCargaId() { return cargaId; }
    public void setCargaId(Long v) { this.cargaId = v; }
    public Long getMotoristaId() { return motoristaId; }
    public void setMotoristaId(Long v) { this.motoristaId = v; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal v) { this.latitude = v; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal v) { this.longitude = v; }
    public LocalDateTime getRegistradoEm() { return registradoEm; }
    public void setRegistradoEm(LocalDateTime v) { this.registradoEm = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
