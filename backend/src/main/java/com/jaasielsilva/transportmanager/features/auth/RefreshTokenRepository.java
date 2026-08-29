package com.jaasielsilva.transportmanager.features.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Reuso de um token ja rotacionado significa que alguem copiou o cookie.
     * A resposta certa e derrubar a cadeia inteira daquele usuario, nao apenas
     * recusar aquele token — o atacante ainda teria os outros.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken r SET r.revogadoEm = CURRENT_TIMESTAMP
             WHERE r.usuarioId = :usuarioId AND r.revogadoEm IS NULL
            """)
    void revogarTodosDoUsuario(@Param("usuarioId") Long usuarioId);
}
