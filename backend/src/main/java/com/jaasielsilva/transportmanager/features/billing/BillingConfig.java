package com.jaasielsilva.transportmanager.features.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Molde do kit — features/billing/BillingConfig.java
 *
 * Implementacao neutra de GatewayBilling para a aplicacao SUBIR antes de o
 * gateway estar escolhido. Sem ela o Spring nao encontra o bean e o projeto
 * novo nem inicia — o que travaria todo o desenvolvimento do dominio por causa
 * de uma decisao comercial que nao precisa ser tomada no dia 1.
 *
 * Comportamento: recusa qualquer webhook (assinatura nunca valida) e registra
 * um aviso na subida. Nada e liberado por engano.
 *
 * @ConditionalOnMissingBean: no dia em que voce criar o GatewayStripe (ou
 * Asaas, ou Mercado Pago) como @Service, ele assume automaticamente e esta
 * classe sai de cena sem precisar mexer aqui.
 */
@Configuration
public class BillingConfig {

    private static final Logger log = LoggerFactory.getLogger(BillingConfig.class);

    @Bean
    @ConditionalOnMissingBean(GatewayBilling.class)
    public GatewayBilling gatewayNaoConfigurado() {
        log.warn("""
                GatewayBilling nao configurado — cobranca desativada.
                Webhooks serao recusados e a regua de dunning nunca sera acionada
                por evento externo. Implemente GatewayBilling para o seu provedor
                antes do go-live comercial.""");

        return new GatewayBilling() {

            @Override
            public boolean assinaturaValida(String payloadBruto, String assinaturaRecebida) {
                // Recusar por padrao: um gateway "aberto" liberaria assinatura
                // para qualquer requisicao vinda da internet.
                log.warn("Webhook recebido sem gateway configurado — recusado");
                return false;
            }

            @Override
            public EventoBilling interpretar(String payloadBruto) {
                throw new UnsupportedOperationException("GatewayBilling nao configurado");
            }

            @Override
            public String urlDeRegularizacao(Long empresaId) {
                return "/plano";
            }
        };
    }
}
