package com.jaasielsilva.transportmanager.features.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * E-mails dos TENANT_ADMIN ativos — destinatarios dos avisos de cobranca.
     * Query nativa porque atravessa a tabela de roles; o filtro por empresa_id
     * e explicito, como manda a regra para native query.
     */
    @Query(value = """
            SELECT u.email FROM usuarios u
              JOIN usuario_roles r ON r.usuario_id = u.id
             WHERE u.empresa_id = :empresaId
               AND r.role = 'TENANT_ADMIN'
               AND u.ativo = TRUE
               AND u.deleted_at IS NULL
            """, nativeQuery = true)
    List<String> buscarAdminsDaEmpresa(@Param("empresaId") Long empresaId);

    /**
     * Busca sem tenant — o login acontece antes de saber a empresa. Funciona
     * porque Usuario nao tem @TenantId (ver o javadoc da entity).
     */
    Optional<Usuario> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmail(String email);

    /** Consumo da quota MAX_USUARIOS do plano. */
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}
