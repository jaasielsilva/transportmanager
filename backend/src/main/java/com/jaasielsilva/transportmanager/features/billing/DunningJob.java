package com.jaasielsilva.transportmanager.features.billing;

import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.features.platform.EmpresaRepository;
import java.time.LocalDate;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Molde do kit — copiar para features/billing/DunningJob.java
 *
 * Executa a ReguaDeDunning uma vez por dia. Aqui ficam SO os efeitos colaterais;
 * a regra esta em ReguaDeDunning (pura e testavel).
 *
 * Tres propriedades que este job precisa ter:
 *
 * 1. IDEMPOTENTE — rodar duas vezes no mesmo dia nao pode reenviar e-mail. Quem
 *    garante e a consulta em dunning_eventos antes de aplicar a etapa, com o
 *    UNIQUE (empresa_id, etapa, acao) como rede de seguranca no banco.
 * 2. SEM FILTRO DE TENANT — varre todas as empresas. E um dos tres lugares onde
 *    TenantContext.semFiltroDeTenant(...) e legitimo.
 * 3. TOLERANTE A FALHA INDIVIDUAL — uma empresa que estoura excecao nao pode
 *    impedir o processamento das outras. Por isso o try/catch por empresa, e
 *    uma transacao por empresa (dentro do AssinaturaService), nunca no laco.
 *
 * A transacao fica no AssinaturaService, e nao aqui, por um motivo tecnico: o
 * Spring aplica @Transactional por proxy, e metodo chamado de dentro da propria
 * classe nao passa pelo proxy — a anotacao seria ignorada em silencio.
 *
 * Dependencia: net.javacrumbs.shedlock:shedlock-spring + shedlock-provider-jdbc-template
 * (tabela shedlock criada no V2). Sem ela, duas instancias cobram o cliente em dobro.
 */
@Component
public class DunningJob {

    private static final Logger log = LoggerFactory.getLogger(DunningJob.class);

    private final EmpresaRepository empresaRepository;
    private final AssinaturaService assinaturaService;

    public DunningJob(EmpresaRepository empresaRepository, AssinaturaService assinaturaService) {
        this.empresaRepository = empresaRepository;
        this.assinaturaService = assinaturaService;
    }

    /**
     * 04:00 — depois do backup (03:15) e antes do horario comercial, para o
     * cliente encontrar o e-mail ao comecar o dia.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "dunning", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void executar() {
        LocalDate hoje = LocalDate.now();
        log.info("Dunning: iniciando varredura de {}", hoje);

        List<Long> emAtraso = TenantContext.semFiltroDeTenant(
                () -> empresaRepository.buscarEmAtraso());

        int avancadas = 0;
        for (Long empresaId : emAtraso) {
            try {
                // A chamada atravessa o proxy do Spring (outro bean), entao o
                // @Transactional de la realmente vale. Cada empresa e uma
                // transacao propria: uma que falhe nao desfaz as anteriores.
                boolean avancou = TenantContext.semFiltroDeTenant(
                        () -> assinaturaService.avancarSeNecessario(empresaId, hoje));
                if (avancou) {
                    avancadas++;
                }
            } catch (Exception e) {
                // Uma empresa com problema nao pode travar a regua das demais.
                log.error("Dunning: falha na empresa {}", empresaId, e);
            }
        }

        log.info("Dunning: {} empresas em atraso, {} avancaram de etapa",
                emAtraso.size(), avancadas);
    }

    /**
     * Trial que expirou vira PAST_DUE e entra na mesma regua — o caminho de
     * volta e identico ao de quem teve o cartao recusado.
     */
    @Scheduled(cron = "0 30 4 * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "expiracao-trial", lockAtMostFor = "PT30M")
    public void expirarTrials() {
        List<Long> expirados = TenantContext.semFiltroDeTenant(
                () -> empresaRepository.buscarTrialsExpirados(LocalDate.now()));

        for (Long empresaId : expirados) {
            try {
                TenantContext.semFiltroDeTenant(() -> {
                    assinaturaService.expirarTrial(empresaId);
                    return null;
                });
            } catch (Exception e) {
                log.error("Expiracao de trial: falha na empresa {}", empresaId, e);
            }
        }
        log.info("Expiracao de trial: {} empresas processadas", expirados.size());
    }
}
