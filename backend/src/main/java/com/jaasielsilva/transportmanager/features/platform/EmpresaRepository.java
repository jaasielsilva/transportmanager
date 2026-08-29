package com.jaasielsilva.transportmanager.features.platform;

import com.jaasielsilva.transportmanager.features.billing.AssinaturaStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    Optional<Empresa> findByDocumento(String documento);

    /**
     * Modulos que o plano da empresa habilita. Vira a claim `modules` do JWT.
     *
     * Native query: `planos` e `plano_modulos` sao tabelas de configuracao da
     * plataforma, sem entity mapeada. Nao ha risco de tenant aqui — o filtro e
     * o proprio id da empresa.
     */
    @Query(value = """
            SELECT pm.modulo FROM plano_modulos pm
              JOIN empresas e ON e.plano_id = pm.plano_id
             WHERE e.id = :empresaId
            """, nativeQuery = true)
    List<String> modulosDaEmpresa(@Param("empresaId") Long empresaId);

    /**
     * Quota do plano para uma chave (MAX_USUARIOS, MAX_CADASTROS...). Optional
     * vazio = sem limite configurado = ilimitado (ver QuotaService).
     *
     * Native pelo mesmo motivo da consulta acima: plano_limites e configuracao
     * da plataforma, sem entity mapeada, e o filtro e o id da empresa.
     */
    @Query(value = """
            SELECT pl.valor FROM plano_limites pl
              JOIN empresas e ON e.plano_id = pl.plano_id
             WHERE e.id = :empresaId AND pl.chave = :chave
            """, nativeQuery = true)
    Optional<Long> limiteDoPlano(@Param("empresaId") Long empresaId, @Param("chave") String chave);

    /**
     * Status vai como parametro, nao como literal na JPQL: enum literal em
     * JPQL exige nome totalmente qualificado e quebra ao renomear o pacote.
     */
    @Query("SELECT e.id FROM Empresa e WHERE e.assinaturaStatus = :status AND e.deletedAt IS NULL")
    List<Long> buscarIdsPorStatus(@Param("status") AssinaturaStatus status);

    /** Empresas na regua de dunning — varredura diaria do DunningJob. */
    default List<Long> buscarEmAtraso() {
        return buscarIdsPorStatus(AssinaturaStatus.PAST_DUE);
    }

    /** Trials vencidos — entram na mesma regua de quem teve o cartao recusado. */
    @Query("""
            SELECT e.id FROM Empresa e
             WHERE e.assinaturaStatus = :status
               AND e.trialExpiraEm < :hoje
               AND e.deletedAt IS NULL
            """)
    List<Long> buscarTrialsVencidos(@Param("status") AssinaturaStatus status,
                                    @Param("hoje") LocalDate hoje);

    default List<Long> buscarTrialsExpirados(LocalDate hoje) {
        return buscarTrialsVencidos(AssinaturaStatus.TRIALING, hoje);
    }

    /** Empresas cujo periodo de retencao terminou — rotina de purge. */
    @Query("""
            SELECT e.id FROM Empresa e
             WHERE e.purgeEm IS NOT NULL AND e.purgeEm <= :hoje
            """)
    List<Long> buscarParaPurge(@Param("hoje") LocalDate hoje);

    /**
     * Projecao usada pelo AssinaturaAccessInterceptor, que roda em TODA
     * requisicao: carregar a Empresa inteira a cada chamada de API seria
     * desperdicio. Quando a base crescer, cachear esta consulta (TTL curto)
     * e o proximo passo natural.
     */
    interface SituacaoAssinatura {
        AssinaturaStatus getAssinaturaStatus();
        Integer getDunningEtapa();
    }

    Optional<SituacaoAssinatura> findSituacaoById(Long id);
}
