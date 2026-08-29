package com.jaasielsilva.transportmanager.config.tenant;

import java.util.Collection;
import java.util.Set;

/**
 * Molde do kit — copiar para config/tenant/TenantContext.java
 *
 * Guarda o tenant da requisicao atual e os modulos que o plano dele habilita.
 * Preenchido pelo filtro JWT DEPOIS de validar o token — nunca a partir de
 * body, query param ou header do cliente.
 *
 * ThreadLocal exige limpeza no finally do filtro: sem isso o pool de threads
 * do Tomcat reaproveita a thread e a proxima requisicao herda o tenant da
 * anterior. E o pior bug possivel neste padrao — vazamento de dados entre
 * empresas, silencioso e intermitente.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();

    /** true enquanto um fluxo /api/v1/platform/** legitimamente ignora o filtro. */
    private static final ThreadLocal<Boolean> MODO_PLATAFORMA = ThreadLocal.withInitial(() -> false);

    /**
     * Modulos que o plano do tenant habilita, lidos da claim `modules` do JWT.
     *
     * Ficam aqui, e nao numa consulta ao banco a cada requisicao, porque vem
     * assinados no token: mudou de plano, vale no proximo refresh (no maximo
     * 15 min). O custo dessa defasagem e menor que o de uma consulta extra em
     * todo endpoint do sistema.
     */
    private static final ThreadLocal<Set<String>> MODULOS = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long empresaId) {
        TENANT.set(empresaId);
    }

    public static void setModulos(Collection<String> modulos) {
        MODULOS.set(modulos == null ? Set.of() : Set.copyOf(modulos));
    }

    public static Set<String> getModulos() {
        Set<String> modulos = MODULOS.get();
        return modulos == null ? Set.of() : modulos;
    }

    public static boolean temModulo(String modulo) {
        return getModulos().contains(modulo);
    }

    public static Long get() {
        return TENANT.get();
    }

    public static Long getObrigatorio() {
        Long id = TENANT.get();
        if (id == null) {
            throw new IllegalStateException("TenantContext vazio — requisicao sem tenant resolvido");
        }
        return id;
    }

    public static boolean isModoPlataforma() {
        return Boolean.TRUE.equals(MODO_PLATAFORMA.get());
    }

    /**
     * Executa um trecho SEM filtro de tenant. Uso exclusivo de fluxos
     * PLATFORM_ADMIN em /api/v1/platform/** e de jobs agendados que varrem
     * todos os tenants (purge, expiracao de trial, dunning).
     *
     * Jamais chame isto dentro de um fluxo de tenant comum — e a unica forma
     * de furar o isolamento, entao toda chamada deve ser obvia na revisao.
     */
    public static <T> T semFiltroDeTenant(java.util.function.Supplier<T> acao) {
        MODO_PLATAFORMA.set(true);
        try {
            return acao.get();
        } finally {
            MODO_PLATAFORMA.remove();
        }
    }

    /** Chamar SEMPRE no finally do filtro. */
    public static void clear() {
        TENANT.remove();
        MODULOS.remove();
        MODO_PLATAFORMA.remove();
    }
}
