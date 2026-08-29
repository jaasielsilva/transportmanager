package com.jaasielsilva.transportmanager.features.auth;

import com.jaasielsilva.transportmanager.features.platform.ConsumoDeQuota;
import org.springframework.stereotype.Component;

/**
 * Molde do kit — features/auth/ConsumoDeUsuarios.java
 *
 * Consumo da quota MAX_USUARIOS. Usa o proprio repositorio da feature: Usuario
 * nao tem @TenantId (o login acontece antes de existir tenant), entao o filtro
 * por empresa e explicito aqui.
 *
 * Modelo para as demais: uma classe pequena, ao lado do dado que ela conta.
 */
@Component
public class ConsumoDeUsuarios implements ConsumoDeQuota {

    private final UsuarioRepository usuarioRepository;

    public ConsumoDeUsuarios(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public String chave() {
        return "MAX_USUARIOS";
    }

    @Override
    public long consumoDe(Long empresaId) {
        return usuarioRepository.countByEmpresaIdAndDeletedAtIsNull(empresaId);
    }
}
