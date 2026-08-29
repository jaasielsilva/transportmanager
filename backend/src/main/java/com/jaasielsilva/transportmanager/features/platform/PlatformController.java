package com.jaasielsilva.transportmanager.features.platform;

import com.jaasielsilva.transportmanager.common.ApiResponse;
import com.jaasielsilva.transportmanager.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Molde do kit — copiar para features/platform/PlatformController.java
 *
 * Area do DONO do SaaS, nao do cliente. Enquanto o TENANT_ADMIN enxerga a
 * empresa dele, aqui se enxerga a base inteira: quanto entra por mes, quem
 * esta em atraso, quem vence o trial amanha, qual modulo ninguem usa.
 *
 * Sem isto a operacao e feita por SELECT manual no MySQL de producao. Funciona
 * com 3 clientes; com 30 voce nao percebe que 4 estao em PAST_DUE ha duas semanas.
 *
 * Duas regras estruturais:
 *   - @PreAuthorize em PLATFORM_ADMIN na CLASSE — nenhum endpoint aqui pode
 *     escapar por esquecimento numa anotacao individual
 *   - prefixo /platform, isento do interceptor de assinatura: quem opera a
 *     plataforma nunca e bloqueado pela regua de dunning de um cliente
 */
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformController {

    private final PlatformMetricsService metricsService;
    private final PlatformTenantService tenantService;

    public PlatformController(PlatformMetricsService metricsService,
                              PlatformTenantService tenantService) {
        this.metricsService = metricsService;
        this.tenantService = tenantService;
    }

    /** Visao geral — a tela que se abre de manha. */
    @GetMapping("/dashboard")
    public ApiResponse<PlatformDtos.Dashboard> dashboard() {
        return ApiResponse.ok(metricsService.dashboard());
    }

    /**
     * Lista de tenants com filtro por situacao.
     * ?situacao=PAST_DUE|TRIAL_EXPIRANDO|CANCELED|ACTIVE
     */
    @GetMapping("/tenants")
    public ApiResponse<PageResponse<PlatformDtos.TenantResumo>> tenants(
            @RequestParam(required = false) String situacao,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ApiResponse.ok(tenantService.listar(situacao, q, pageable));
    }

    @GetMapping("/tenants/{id}")
    public ApiResponse<PlatformDtos.TenantDetalhe> tenant(@PathVariable Long id) {
        return ApiResponse.ok(tenantService.detalhar(id));
    }

    /**
     * Historico da regua de dunning de um tenant — o outro lado do job:
     * a regua age sozinha, aqui se ve o que ela fez.
     */
    @GetMapping("/tenants/{id}/dunning")
    public ApiResponse<java.util.List<PlatformDtos.DunningEvento>> dunning(@PathVariable Long id) {
        return ApiResponse.ok(tenantService.historicoDeDunning(id));
    }

    /**
     * Fila de eventos do gateway que nao foram processados. E o que torna o
     * botao de reprocessar utilizavel: sem a lista, alguem teria que achar o
     * id do evento no banco de producao.
     */
    @GetMapping("/webhooks/falhas")
    public ApiResponse<java.util.List<PlatformDtos.WebhookFalha>> webhooksComFalha() {
        return ApiResponse.ok(tenantService.webhooksPendentes());
    }

    /**
     * Reprocessa um webhook que falhou. Sem isto, um evento perdido vira
     * correcao manual no banco de producao.
     */
    @PostMapping("/webhooks/{eventoId}/reprocessar")
    public ApiResponse<Void> reprocessarWebhook(@PathVariable String eventoId) {
        tenantService.reprocessarWebhook(eventoId);
        return ApiResponse.ok(null, "Evento reenfileirado.");
    }

    /**
     * Concessao manual de prazo: pausa a regua por N dias sem marcar como pago.
     * Existe porque cliente grande negocia — e sem este botao a alternativa e
     * um UPDATE na mao em producao. Auditado.
     */
    @PostMapping("/tenants/{id}/prorrogar")
    public ApiResponse<Void> prorrogar(@PathVariable Long id, @RequestParam int dias) {
        tenantService.prorrogarCobranca(id, dias);
        return ApiResponse.ok(null, "Cobranca prorrogada por " + dias + " dias.");
    }
}
