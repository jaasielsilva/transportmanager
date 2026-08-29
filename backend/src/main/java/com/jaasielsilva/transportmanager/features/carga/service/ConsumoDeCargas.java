package com.jaasielsilva.transportmanager.features.carga.service;

import com.jaasielsilva.transportmanager.features.platform.ConsumoDeQuota;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Molde do kit — consumo da quota MAX_CADASTROS.
 *
 * Copie esta classe para toda feature que tiver limite de plano: e ela que faz
 * o limite aparecer na ficha do tenant no painel do dono, ANTES do cliente
 * esbarrar nele. Quota que so aparece no 409 e uma conversa comercial perdida.
 *
 * SQL direto, e nao o CargaRepository, por um motivo especifico: o
 * repositorio e filtrado pelo @TenantId, ou seja, pelo tenant da REQUISICAO —
 * e aqui quem pergunta e o PLATFORM_ADMIN sobre a empresa de outra pessoa. O
 * filtro por empresa_id, portanto, e explicito e proposital, como manda a
 * regra para consulta nativa.
 */
@Component
public class ConsumoDeCargas implements ConsumoDeQuota {

    private final JdbcTemplate jdbc;

    public ConsumoDeCargas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String chave() {
        return "MAX_CADASTROS";
    }

    @Override
    public long consumoDe(Long empresaId) {
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cargas
                 WHERE empresa_id = ? AND deleted_at IS NULL
                """, Long.class, empresaId);
        return total == null ? 0 : total;
    }
}
