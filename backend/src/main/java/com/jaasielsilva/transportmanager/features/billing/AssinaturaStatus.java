package com.jaasielsilva.transportmanager.features.billing;

/**
 * Molde do kit — features/billing/AssinaturaStatus.java
 *
 * Situacao comercial do tenant. Sincronizada pelo webhook do gateway; nenhum
 * outro ponto do sistema escreve nela fora do AssinaturaService.
 */
public enum AssinaturaStatus {
    /** Periodo de teste. Nao entra no MRR — ainda nao e receita. */
    TRIALING,
    /** Pagando em dia. */
    ACTIVE,
    /** Pagamento falhou: esta na regua de dunning. */
    PAST_DUE,
    /** Cancelada. Dados retidos ate purge_em (portabilidade LGPD). */
    CANCELED
}
