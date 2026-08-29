package com.jaasielsilva.transportmanager.features.billing;

/**
 * Molde do kit — copiar para features/billing/GatewayBilling.java
 *
 * Fronteira com o provedor de pagamento. TUDO que e especifico de Stripe, Asaas
 * ou Mercado Pago fica atras desta interface: nomes de evento, formato do
 * payload, algoritmo do HMAC, ids.
 *
 * O resto do sistema (AssinaturaService, DunningJob, painel) so conhece
 * EventoBilling. Trocar de gateway vira uma implementacao nova, nao uma
 * reescrita do ciclo comercial.
 *
 * Nunca guardamos dado de cartao — so plano, status e os ids do gateway.
 */
public interface GatewayBilling {

    /**
     * Valida o HMAC do payload BRUTO com o secret do gateway
     * (BILLING_WEBHOOK_SECRET no .env).
     *
     * Usar comparacao de tempo constante (MessageDigest.isEqual) — comparar com
     * equals() abre timing attack sobre a assinatura.
     */
    boolean assinaturaValida(String payloadBruto, String assinaturaRecebida);

    /** Traduz o payload do provedor para o evento neutro do sistema. */
    EventoBilling interpretar(String payloadBruto);

    /** URL do portal de pagamento do cliente — usada no e-mail e no banner de dunning. */
    String urlDeRegularizacao(Long empresaId);

    /**
     * Evento neutro. Tudo que o ciclo comercial precisa saber, independente
     * de quem cobrou.
     */
    record EventoBilling(
            String eventoId,      // id do evento NO GATEWAY — chave de idempotencia
            Tipo tipo,
            Long empresaId
    ) {
        public enum Tipo {
            PAGAMENTO_CONFIRMADO,
            PAGAMENTO_FALHOU,
            ASSINATURA_CANCELADA,
            /** Evento que o gateway manda e nao nos interessa — registrado e ignorado. */
            IGNORADO
        }
    }
}
