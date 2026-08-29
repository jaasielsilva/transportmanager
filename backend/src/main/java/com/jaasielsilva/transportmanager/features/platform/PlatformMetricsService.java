package com.jaasielsilva.transportmanager.features.platform;

import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Molde do kit — copiar para features/platform/PlatformMetricsService.java
 *
 * As metricas do dono do SaaS. Usa JdbcTemplate de proposito: sao agregacoes
 * sobre TODAS as empresas, nao ha entidade de dominio por tras, e SQL direto e
 * mais honesto (e mais rapido) que forcar JPQL em cima disso.
 *
 * Sobre o filtro de tenant: `empresas` nao e entity com @TenantId — ela E o
 * tenant. Nao ha filtro automatico para escapar aqui. Onde as consultas tocarem
 * tabelas de negocio (uso por modulo), envolver em semFiltroDeTenant.
 *
 * Cachear (@Cacheable, TTL de ~5 min) quando a base passar de algumas centenas
 * de tenants: sao varios full scans e ninguem precisa de MRR em tempo real.
 */
@Service
public class PlatformMetricsService {

    private final JdbcTemplate jdbc;

    public PlatformMetricsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PlatformDtos.Dashboard dashboard() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioDoMes = hoje.withDayOfMonth(1);

        return new PlatformDtos.Dashboard(
                receitaRecorrenteMensal(),
                contarPorStatus("ACTIVE"),
                contarPorStatus("TRIALING"),
                contarPorStatus("PAST_DUE"),
                trialsExpirandoEm(7),
                canceladasNoPeriodo(inicioDoMes),
                taxaDeChurn(inicioDoMes),
                taxaDeAtivacao(hoje.minusDays(30)),
                receitaEmRisco(),
                usoPorModulo());
    }

    /**
     * MRR — soma do preco dos planos das assinaturas ativas.
     * TRIALING nao entra: ainda nao e receita. Contar trial no MRR e a forma
     * mais comum de inflar o proprio numero e tomar decisao errada.
     */
    private BigDecimal receitaRecorrenteMensal() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.preco_mensal), 0)
                  FROM empresas e
                  JOIN planos p ON p.id = e.plano_id
                 WHERE e.assinatura_status = 'ACTIVE'
                   AND e.deleted_at IS NULL
                """, BigDecimal.class);
    }

    /**
     * Receita em risco: quanto do MRR esta preso na regua de dunning.
     * E o numero que justifica olhar o painel hoje e nao amanha.
     */
    private BigDecimal receitaEmRisco() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.preco_mensal), 0)
                  FROM empresas e
                  JOIN planos p ON p.id = e.plano_id
                 WHERE e.assinatura_status = 'PAST_DUE'
                   AND e.deleted_at IS NULL
                """, BigDecimal.class);
    }

    private long contarPorStatus(String status) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM empresas
                 WHERE assinatura_status = ? AND deleted_at IS NULL
                """, Long.class, status);
    }

    /** Trials que vencem nos proximos N dias — a fila de contato comercial. */
    private long trialsExpirandoEm(int dias) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM empresas
                 WHERE assinatura_status = 'TRIALING'
                   AND trial_expira_em BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL ? DAY)
                   AND deleted_at IS NULL
                """, Long.class, dias);
    }

    private long canceladasNoPeriodo(LocalDate desde) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM empresas
                 WHERE assinatura_status = 'CANCELED' AND cancelada_em >= ?
                """, Long.class, desde);
    }

    /**
     * Churn do mes = cancelamentos / base ativa no inicio do periodo.
     * Acima de 5% ao mes nao se cresce: entra cliente pela porta da frente e
     * sai pela de tras.
     */
    private BigDecimal taxaDeChurn(LocalDate inicioDoMes) {
        Long base = jdbc.queryForObject("""
                SELECT COUNT(*) FROM empresas
                 WHERE deleted_at IS NULL
                   AND created_at < ?
                   AND (cancelada_em IS NULL OR cancelada_em >= ?)
                """, Long.class, inicioDoMes, inicioDoMes);

        if (base == null || base == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(canceladasNoPeriodo(inicioDoMes) * 100.0 / base)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Ativacao: dos tenants criados no periodo, quantos chegaram ao momento
     * "aha" (coluna ativada_em, definida por projeto).
     *
     * E a metrica que explica o churn de trial. Trial que nao ativa nao vira
     * cliente, e o problema esta no onboarding, nao no preco.
     */
    private BigDecimal taxaDeAtivacao(LocalDate desde) {
        Map<String, Object> r = jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN ativada_em IS NOT NULL THEN 1 ELSE 0 END) AS ativadas
                  FROM empresas
                 WHERE created_at >= ? AND deleted_at IS NULL
                """, desde);

        long total = ((Number) r.get("total")).longValue();
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        long ativadas = r.get("ativadas") == null ? 0 : ((Number) r.get("ativadas")).longValue();
        return BigDecimal.valueOf(ativadas * 100.0 / total)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Quantos tenants tem cada modulo habilitado pelo plano.
     * Modulo que quase ninguem tem e candidato a ser cortado ou a subir de
     * plano; o mais usado e argumento de preco.
     */
    private List<PlatformDtos.UsoModulo> usoPorModulo() {
        return TenantContext.semFiltroDeTenant(() -> jdbc.query("""
                SELECT pm.modulo, COUNT(DISTINCT e.id) AS tenants
                  FROM plano_modulos pm
                  JOIN empresas e ON e.plano_id = pm.plano_id
                 WHERE e.deleted_at IS NULL
                   AND e.assinatura_status IN ('ACTIVE', 'TRIALING')
                 GROUP BY pm.modulo
                 ORDER BY tenants DESC
                """, (rs, i) -> new PlatformDtos.UsoModulo(rs.getString("modulo"), rs.getLong("tenants"))));
    }
}
