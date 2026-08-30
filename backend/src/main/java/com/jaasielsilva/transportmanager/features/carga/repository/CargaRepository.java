package com.jaasielsilva.transportmanager.features.carga.repository;

import com.jaasielsilva.transportmanager.features.carga.entity.Carga;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Molde do kit — features/carga/repository/CargaRepository.java
 *
 * Nenhuma query aqui menciona empresa_id: o {@code @TenantId} da entity ja
 * injeta o filtro em todas elas. Escrever o filtro de novo seria redundante —
 * e daria a impressao errada de que quem esquecer fica desprotegido.
 *
 * A regra que continua valendo por conta propria: query NATIVA nao e filtrada.
 * Se um dia aparecer uma aqui, ela leva WHERE empresa_id = :empresaId na mao.
 */
public interface CargaRepository extends JpaRepository<Carga, Long> {

    /**
     * Busca da listagem. O parametro nunca chega nulo (o service normaliza para
     * ""): busca vazia vira LIKE '%%', que casa com tudo. Com ':q IS NULL OR'
     * o Hibernate precisa inferir o tipo de um parametro nulo e a query quebra
     * em tempo de execucao, nao de compilacao — o pior momento para descobrir.
     */
    @Query("""
            SELECT r FROM Carga r
             WHERE r.deletedAt IS NULL
               AND (LOWER(r.nome)  LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(COALESCE(r.email, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR COALESCE(r.documento, '')    LIKE CONCAT('%', :q, '%'))
            """)
    Page<Carga> buscar(@Param("q") String q, Pageable pageable);

    /**
     * Mesma busca, com motoristaId/status opcionais — usada por "Minhas
     * entregas" (GET /cargas?motoristaId=X&status=EM_TRANSITO). ':motoristaId
     * IS NULL OR' e ':status IS NULL OR' funcionam aqui porque os dois
     * parametros tem tipo conhecido (Long/String), ao contrario do :q vazio.
     */
    @Query("""
            SELECT r FROM Carga r
             WHERE r.deletedAt IS NULL
               AND (LOWER(r.nome)  LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(COALESCE(r.email, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR COALESCE(r.documento, '')    LIKE CONCAT('%', :q, '%'))
               AND (:motoristaId IS NULL OR r.motoristaId = :motoristaId)
               AND (:status IS NULL OR r.status = :status)
            """)
    Page<Carga> buscar(@Param("q") String q,
                        @Param("motoristaId") Long motoristaId,
                        @Param("status") String status,
                        Pageable pageable);

    /**
     * Registro de outro tenant simplesmente nao e encontrado — o service
     * transforma isso em 404. E o comportamento correto: 403 confirmaria que o
     * id existe em alguma empresa.
     */
    Optional<Carga> findByIdAndDeletedAtIsNull(Long id);

    /** Duplicidade de documento dentro da empresa (o tenant vem do @TenantId). */
    boolean existsByDocumentoAndDeletedAtIsNull(String documento);

    /** Mesma checagem na edicao — sem acusar o proprio registro de duplicado. */
    boolean existsByDocumentoAndDeletedAtIsNullAndIdNot(String documento, Long id);

    /** Consumo atual da quota do plano. */
    long countByDeletedAtIsNull();
}
