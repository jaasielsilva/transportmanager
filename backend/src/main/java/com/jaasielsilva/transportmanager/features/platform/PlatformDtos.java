package com.jaasielsilva.transportmanager.features.platform;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Molde do kit — copiar para features/platform/PlatformDtos.java, ou quebrar em
 * um record por arquivo em features/platform/dto/ (preferido quando crescerem).
 *
 * Nenhum destes carrega dado operacional do cliente: o painel da plataforma
 * mostra situacao comercial, nao conteudo dos tenants. Para ver o sistema pelos
 * olhos do cliente existe a impersonacao, que e auditada.
 */
public final class PlatformDtos {

    private PlatformDtos() {}

    /** Visao geral do negocio — a tela que se abre de manha. */
    public record Dashboard(
            BigDecimal mrr,                  // receita recorrente mensal (so ACTIVE)
            long tenantsAtivos,
            long tenantsEmTrial,
            long tenantsEmAtraso,            // PAST_DUE — na regua de dunning
            long trialsExpirandoEm7Dias,     // fila de contato comercial
            long canceladasNoMes,
            BigDecimal churnPercentual,      // acima de 5%/mes nao se cresce
            BigDecimal ativacaoPercentual,   // trial que nao ativa nao vira cliente
            BigDecimal receitaEmRisco,       // quanto do MRR esta preso no dunning
            List<UsoModulo> usoPorModulo
    ) {}

    public record UsoModulo(String modulo, long tenants) {}

    /** Linha da listagem de tenants. */
    public record TenantResumo(
            Long id,
            String razaoSocial,
            String plano,
            String assinaturaStatus,
            int dunningEtapa,
            LocalDate trialExpiraEm,
            long usuarios,
            LocalDateTime ultimoAcesso
    ) {}

    /** Ficha do tenant. */
    public record TenantDetalhe(
            Long id,
            String razaoSocial,
            String documento,
            String plano,
            BigDecimal precoMensal,
            String assinaturaStatus,
            int dunningEtapa,
            String nivelAcesso,              // NORMAL | SOMENTE_LEITURA | SUSPENSO
            LocalDateTime pastDueDesde,
            LocalDate trialExpiraEm,
            LocalDateTime ativadaEm,
            LocalDate purgeEm,
            long usuarios,
            List<LimiteConsumo> limites      // quota do plano x consumo atual
    ) {}

    /** Limite do plano e quanto ja foi usado — antecipa a conversa de upgrade. */
    public record LimiteConsumo(String chave, long limite, long consumo) {}

    /** Evento do gateway que ainda nao foi processado — fila de reprocessamento. */
    public record WebhookFalha(
            String eventoId,
            String tipo,
            String erro,
            LocalDateTime em) {}

    /** Linha do historico da regua de dunning. */
    public record DunningEvento(
            int etapa,
            String acao,                     // EMAIL_ENVIADO | SOMENTE_LEITURA | SUSPENSO | REGULARIZADO
            String detalhes,
            LocalDateTime em
    ) {}
}
