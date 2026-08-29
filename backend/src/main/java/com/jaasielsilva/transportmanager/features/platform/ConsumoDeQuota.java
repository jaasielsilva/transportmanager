package com.jaasielsilva.transportmanager.features.platform;

/**
 * Molde do kit — features/platform/ConsumoDeQuota.java
 *
 * Diz quanto um tenant JA consumiu de uma quota do plano (plano_limites).
 *
 * Existe para que a plataforma nao precise conhecer as tabelas de negocio: o
 * painel pergunta "quanto esse tenant usou de MAX_CADASTROS?" e quem responde
 * e a propria feature dona do dado. Feature nova com limite = uma classe nova
 * implementando isto, e o painel passa a mostrar sozinho.
 *
 * A alternativa — um switch gigante no PlatformTenantService — obriga a
 * plataforma a importar todo modulo do sistema e, na pratica, garante que
 * alguem vai esquecer de atualizar o switch.
 *
 * O tenant vem por PARAMETRO, nunca do TenantContext: quem chama e o
 * PLATFORM_ADMIN olhando a ficha de OUTRA empresa.
 */
public interface ConsumoDeQuota {

    /** Chave em plano_limites (ex.: MAX_USUARIOS, MAX_CADASTROS). */
    String chave();

    long consumoDe(Long empresaId);
}
