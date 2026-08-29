package com.jaasielsilva.transportmanager.features.billing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DunningEventoRepository extends JpaRepository<DunningEvento, Long> {

    /** Historico exibido no painel da plataforma. */
    List<DunningEvento> findByEmpresaIdOrderByCreatedAtDesc(Long empresaId);

    /**
     * Nao existe consulta de idempotencia aqui, de proposito: a tabela e
     * append-only e uma empresa passa pela regua quantas vezes atrasar.
     * Quem controla o ciclo atual e `empresas.dunning_etapa` — ver o javadoc
     * de AssinaturaService.avancarDunning.
     */
}
