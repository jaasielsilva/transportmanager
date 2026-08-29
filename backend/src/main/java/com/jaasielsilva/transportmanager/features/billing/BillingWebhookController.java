package com.jaasielsilva.transportmanager.features.billing;

import com.jaasielsilva.transportmanager.common.ApiResponse;
import com.jaasielsilva.transportmanager.features.billing.GatewayBilling.EventoBilling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Molde do kit — copiar para features/billing/BillingWebhookController.java
 *
 * Endpoint PUBLICO (sem JWT) — e o gateway que chama, nao o usuario. Por isso
 * as tres protecoes abaixo nao sao opcionais:
 *
 * 1. ASSINATURA — sem validar o HMAC, qualquer um na internet ativa a propria
 *    assinatura com um curl. E a falha mais grave possivel neste endpoint.
 * 2. IDEMPOTENCIA — gateway reentrega o mesmo evento (timeout, retry, replay).
 *    Sem ela, uma reentrega vira liberacao duplicada.
 * 3. RESPONDER 200 RAPIDO — se o processamento demorar, o gateway considera
 *    falha e reenvia. Guardamos o evento, respondemos, processamos.
 *
 * O controller nao tem @Transactional: quem abre transacao e o
 * WebhookEventoService. Anotacao em metodo chamado de dentro da propria classe
 * nao passa pelo proxy do Spring e e ignorada em silencio.
 *
 * Configurar no SecurityConfig:
 *   .requestMatchers("/api/v1/public/webhooks/**").permitAll()
 */
@RestController
@RequestMapping("/api/v1/public/webhooks")
public class BillingWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BillingWebhookController.class);

    private final GatewayBilling gateway;
    private final WebhookEventoService webhookEventoService;

    public BillingWebhookController(GatewayBilling gateway,
                                    WebhookEventoService webhookEventoService) {
        this.gateway = gateway;
        this.webhookEventoService = webhookEventoService;
    }

    @PostMapping("/billing")
    public ResponseEntity<ApiResponse<Void>> receber(
            @RequestBody String payloadBruto,
            @RequestHeader(name = "X-Signature", required = false) String assinatura) {

        // 1. Assinatura. Payload BRUTO — desserializar antes muda os bytes e o
        //    HMAC nunca bate.
        if (!gateway.assinaturaValida(payloadBruto, assinatura)) {
            log.warn("Webhook de billing com assinatura invalida — descartado");
            return ResponseEntity.status(401).body(ApiResponse.error("Assinatura invalida"));
        }

        EventoBilling evento = gateway.interpretar(payloadBruto);

        // 2. Idempotencia. Evento ja conhecido -> 200 sem reprocessar. Responder
        //    erro faria o gateway reenviar para sempre.
        if (!webhookEventoService.registrarSeNovo(evento, payloadBruto)) {
            log.info("Webhook {} ja recebido — ignorado", evento.eventoId());
            return ResponseEntity.ok(ApiResponse.ok(null, "Evento ja processado"));
        }

        // 3. Processamento. Falha aqui NAO devolve erro: o evento esta gravado
        //    (REQUIRES_NEW) e e reprocessavel pelo painel. Devolver 500 so faria
        //    o gateway reenviar um evento que ja temos.
        try {
            webhookEventoService.processar(evento);
            webhookEventoService.marcarProcessado(evento.eventoId());
        } catch (Exception e) {
            log.error("Webhook {}: falha no processamento", evento.eventoId(), e);
            webhookEventoService.marcarErro(evento.eventoId(), e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
