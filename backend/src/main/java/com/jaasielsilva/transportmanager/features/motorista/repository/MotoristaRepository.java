package com.jaasielsilva.transportmanager.features.motorista.repository;

import com.jaasielsilva.transportmanager.features.motorista.entity.Motorista;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Molde do kit — features/carga/repository/CargaRepository.java
 *
 * Nenhuma query aqui menciona empresa_id: o @TenantId da entity ja injeta o
 * filtro em todas elas.
 */
public interface MotoristaRepository extends JpaRepository<Motorista, Long> {

    @Query("""
            SELECT r FROM Motorista r
             WHERE r.deletedAt IS NULL
               AND (LOWER(r.nome) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(COALESCE(r.email, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Motorista> buscar(@Param("q") String q, Pageable pageable);

    Optional<Motorista> findByIdAndDeletedAtIsNull(Long id);

    /** Usado pelo "Minhas entregas": motorista vinculado ao usuario logado. */
    Optional<Motorista> findByUsuarioIdAndDeletedAtIsNull(Long usuarioId);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailAndDeletedAtIsNullAndIdNot(String email, Long id);
}
