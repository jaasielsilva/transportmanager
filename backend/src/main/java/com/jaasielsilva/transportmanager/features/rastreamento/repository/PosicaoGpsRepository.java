package com.jaasielsilva.transportmanager.features.rastreamento.repository;

import com.jaasielsilva.transportmanager.features.rastreamento.entity.PosicaoGps;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Nenhuma query aqui menciona empresa_id: o @TenantId da entity ja injeta o
 * filtro em todas elas.
 */
public interface PosicaoGpsRepository extends JpaRepository<PosicaoGps, Long> {

    Optional<PosicaoGps> findTopByCargaIdOrderByRegistradoEmDesc(Long cargaId);

    /** Trilha percorrida, em ordem cronologica — usada para desenhar o caminho ja feito. */
    List<PosicaoGps> findByCargaIdOrderByRegistradoEmAsc(Long cargaId);
}
