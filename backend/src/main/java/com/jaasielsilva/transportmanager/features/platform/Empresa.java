package com.jaasielsilva.transportmanager.features.platform;

import com.jaasielsilva.transportmanager.common.BaseEntity;
import com.jaasielsilva.transportmanager.features.billing.AssinaturaStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Molde do kit — features/platform/Empresa.java
 *
 * O TENANT. Repare que NAO tem @TenantId: ela nao pertence a um tenant, ela e
 * o tenant. Colocar @TenantId aqui faria a empresa filtrar por si mesma e
 * quebraria login, signup e todo o painel da plataforma.
 *
 * Concentra tambem o estado comercial (plano, assinatura, dunning) porque e
 * disso que o ciclo de vida do cliente e feito.
 */
@Entity
@Table(name = "empresas")
public class Empresa extends BaseEntity {

    @Column(name = "razao_social", nullable = false, length = 150)
    private String razaoSocial;

    @Column(nullable = false, length = 20, unique = true)
    private String documento;

    @Column(name = "plano_id", nullable = false)
    private Long planoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assinatura_status", nullable = false, length = 20)
    private AssinaturaStatus assinaturaStatus = AssinaturaStatus.TRIALING;

    @Column(name = "trial_expira_em")
    private LocalDate trialExpiraEm;

    @Column(name = "gateway_customer_id", length = 80)
    private String gatewayCustomerId;

    @Column(name = "gateway_assinatura_id", length = 80)
    private String gatewayAssinaturaId;

    // --- Regua de dunning ---
    @Column(name = "past_due_desde")
    private LocalDateTime pastDueDesde;

    /** 0 = em dia. 1..4 = D+0, D+3, D+7, D+15. Ver ReguaDeDunning. */
    @Column(name = "dunning_etapa", nullable = false)
    private int dunningEtapa;

    @Column(name = "dunning_notificado_em")
    private LocalDateTime dunningNotificadoEm;

    // --- Ciclo de vida ---
    @Column(name = "cancelada_em")
    private LocalDateTime canceladaEm;

    /** cancelada_em + retencao. Depois disso a rotina de purge exclui de vez. */
    @Column(name = "purge_em")
    private LocalDate purgeEm;

    /** Momento "aha": o cliente entendeu o valor. Metrica de ativacao do painel. */
    @Column(name = "ativada_em")
    private LocalDateTime ativadaEm;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String v) { this.razaoSocial = v; }
    public String getDocumento() { return documento; }
    public void setDocumento(String v) { this.documento = v; }
    public Long getPlanoId() { return planoId; }
    public void setPlanoId(Long v) { this.planoId = v; }
    public AssinaturaStatus getAssinaturaStatus() { return assinaturaStatus; }
    public void setAssinaturaStatus(AssinaturaStatus v) { this.assinaturaStatus = v; }
    public LocalDate getTrialExpiraEm() { return trialExpiraEm; }
    public void setTrialExpiraEm(LocalDate v) { this.trialExpiraEm = v; }
    public String getGatewayCustomerId() { return gatewayCustomerId; }
    public void setGatewayCustomerId(String v) { this.gatewayCustomerId = v; }
    public String getGatewayAssinaturaId() { return gatewayAssinaturaId; }
    public void setGatewayAssinaturaId(String v) { this.gatewayAssinaturaId = v; }
    public LocalDateTime getPastDueDesde() { return pastDueDesde; }
    public void setPastDueDesde(LocalDateTime v) { this.pastDueDesde = v; }
    public int getDunningEtapa() { return dunningEtapa; }
    public void setDunningEtapa(int v) { this.dunningEtapa = v; }
    public LocalDateTime getDunningNotificadoEm() { return dunningNotificadoEm; }
    public void setDunningNotificadoEm(LocalDateTime v) { this.dunningNotificadoEm = v; }
    public LocalDateTime getCanceladaEm() { return canceladaEm; }
    public void setCanceladaEm(LocalDateTime v) { this.canceladaEm = v; }
    public LocalDate getPurgeEm() { return purgeEm; }
    public void setPurgeEm(LocalDate v) { this.purgeEm = v; }
    public LocalDateTime getAtivadaEm() { return ativadaEm; }
    public void setAtivadaEm(LocalDateTime v) { this.ativadaEm = v; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
}
