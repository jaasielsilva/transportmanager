package com.jaasielsilva.transportmanager.config.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Molde do kit — copiar para config/tenant/TenantConfig.java
 *
 * Liga o TenantContext ao Hibernate. A partir daqui toda entity com
 * {@code @TenantId private Long empresaId} tem o filtro por empresa_id
 * aplicado automaticamente em findAll(), JPQL e derived queries.
 *
 * O ganho e esse: esquecer o filtro deixa de ser possivel. O que o @TenantId
 * NAO cobre continua sendo responsabilidade de quem escreve:
 *   - @Query(nativeQuery = true) NAO e filtrada -> evite; se for inevitavel,
 *     WHERE empresa_id = :empresaId manual e atencao redobrada na revisao.
 *   - UNIQUE global quebra multi-tenant: use UNIQUE (empresa_id, campo).
 *   - Fluxos PLATFORM_ADMIN usam TenantContext.semFiltroDeTenant(...).
 */
@Configuration
public class TenantConfig {

    /**
     * Sentinela usada quando nao ha tenant (login, signup, webhook, job).
     *
     * Long, e nao String: o tipo do resolver tem que ser o MESMO do campo
     * anotado com @TenantId nas entities (que e a coluna empresa_id, BIGINT).
     * Com os dois tipos diferentes o Hibernate falha ao vincular o filtro.
     */
    static final Long SEM_TENANT = 0L;

    @Bean
    public CurrentTenantIdentifierResolver<Long> tenantResolver() {
        return new CurrentTenantIdentifierResolver<>() {

            @Override
            public Long resolveCurrentTenantIdentifier() {
                if (TenantContext.isModoPlataforma()) {
                    return SEM_TENANT;
                }
                Long empresaId = TenantContext.get();
                return empresaId != null ? empresaId : SEM_TENANT;
            }

            @Override
            public boolean validateExistingCurrentSessions() {
                return true;
            }

            /**
             * false = permite abrir sessao sem tenant (endpoints publicos:
             * login, signup, webhook de billing, health).
             */
            @Override
            public boolean isRoot(Long tenantId) {
                return SEM_TENANT.equals(tenantId);
            }
        };
    }

    @Bean
    public HibernatePropertiesCustomizer tenantCustomizer(
            CurrentTenantIdentifierResolver<Long> resolver) {
        return props -> props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
