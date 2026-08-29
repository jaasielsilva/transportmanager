package com.jaasielsilva.transportmanager.features.billing;

import com.jaasielsilva.transportmanager.common.AuditoriaService;
import com.jaasielsilva.transportmanager.features.auth.EmailService;
import com.jaasielsilva.transportmanager.features.billing.ReguaDeDunning.NivelAcesso;
import com.jaasielsilva.transportmanager.features.platform.Empresa;
import com.jaasielsilva.transportmanager.features.platform.EmpresaRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Molde do kit — copiar para features/billing/AssinaturaService.java
 *
 * Unico lugar que muda assinatura_status. Duas entradas chegam aqui:
 *   - o webhook do gateway (pagou, falhou, cancelou)
 *   - o DunningJob (o tempo passou)
 *
 * Concentrar as transicoes num so ponto e o que permite auditar e testar o
 * ciclo comercial inteiro. Nenhum outro service escreve nessas colunas.
 */
@Service
public class AssinaturaService {

    private static final Logger log = LoggerFactory.getLogger(AssinaturaService.class);

    private final EmpresaRepository empresaRepository;
    private final DunningEventoRepository dunningEventoRepository;
    private final EmailService emailService;
    private final AuditoriaService auditoriaService;
    private final ReguaDeDunning regua;

    public AssinaturaService(EmpresaRepository empresaRepository,
                             DunningEventoRepository dunningEventoRepository,
                             EmailService emailService,
                             AuditoriaService auditoriaService,
                             ReguaDeDunning regua) {
        this.empresaRepository = empresaRepository;
        this.dunningEventoRepository = dunningEventoRepository;
        this.emailService = emailService;
        this.auditoriaService = auditoriaService;
        this.regua = regua;
    }

    // =================================================================
    // Entradas vindas do gateway (webhook)
    // =================================================================

    /** Pagamento falhou: entra na regua. Idempotente — o gateway reenvia. */
    @Transactional
    public void registrarFalhaDePagamento(Long empresaId) {
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();

        if (empresa.getAssinaturaStatus() == AssinaturaStatus.PAST_DUE) {
            return;                       // ja esta na regua; nao reinicia a contagem
        }

        empresa.setAssinaturaStatus(AssinaturaStatus.PAST_DUE);
        empresa.setPastDueDesde(LocalDateTime.now());
        empresa.setDunningEtapa(ReguaDeDunning.ETAPA_EM_DIA);
        empresaRepository.save(empresa);

        auditoriaService.registrar(empresaId, "BILLING_FALHA", "empresa", empresaId, null);
        log.info("Assinatura: empresa {} entrou em PAST_DUE", empresaId);

        // Nao espera o job da madrugada: o cliente e avisado na hora, que e
        // quando ele ainda lembra qual cartao usou.
        avancarDunning(empresa, 1, NivelAcesso.NORMAL);
    }

    /**
     * Pagamento entrou: sai da regua imediatamente e o acesso volta sozinho.
     * Reativacao manual e o erro classico — o cliente paga e continua bloqueado.
     */
    @Transactional
    public void registrarPagamentoConfirmado(Long empresaId) {
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();

        boolean estavaEmAtraso = empresa.getAssinaturaStatus() != AssinaturaStatus.ACTIVE;

        empresa.setAssinaturaStatus(AssinaturaStatus.ACTIVE);
        empresa.setPastDueDesde(null);
        empresa.setDunningEtapa(ReguaDeDunning.ETAPA_EM_DIA);
        empresa.setDunningNotificadoEm(null);
        empresa.setCanceladaEm(null);
        empresa.setPurgeEm(null);
        empresaRepository.save(empresa);

        if (estavaEmAtraso) {
            registrarEvento(empresa, ReguaDeDunning.ETAPA_EM_DIA, "REGULARIZADO", null);
            auditoriaService.registrar(empresaId, "BILLING_REGULARIZADO", "empresa", empresaId, null);
            log.info("Assinatura: empresa {} regularizada", empresaId);
        }
    }

    /** Cancelamento: dados retidos pelo periodo de retencao (portabilidade LGPD). */
    @Transactional
    public void registrarCancelamento(Long empresaId, int diasDeRetencao) {
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();

        empresa.setAssinaturaStatus(AssinaturaStatus.CANCELED);
        empresa.setCanceladaEm(LocalDateTime.now());
        empresa.setPurgeEm(LocalDate.now().plusDays(diasDeRetencao));
        empresaRepository.save(empresa);

        emailService.enviarCancelamento(empresa);
        auditoriaService.registrar(empresaId, "BILLING_CANCELADO", "empresa", empresaId, null);
        log.info("Assinatura: empresa {} cancelada, purge em {}", empresaId, empresa.getPurgeEm());
    }

    // =================================================================
    // Entrada vinda do tempo (DunningJob)
    // =================================================================

    /**
     * Aplica a regua a uma empresa, se o tempo passado exigir. Chamado pelo
     * DunningJob uma vez por dia.
     *
     * Metodo PUBLICO num bean separado de proposito: o job chamava um metodo
     * @Transactional dele mesmo, e chamada interna nao passa pelo proxy do
     * Spring — a anotacao simplesmente nao valia. A transacao so existe quando
     * a chamada vem de fora, como aqui.
     *
     * @return true se a empresa avancou de etapa.
     */
    @Transactional
    public boolean avancarSeNecessario(Long empresaId, LocalDate hoje) {
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();

        LocalDate desde = empresa.getPastDueDesde() == null
                ? null
                : empresa.getPastDueDesde().toLocalDate();

        int etapaDevida = regua.etapaPara(desde, hoje);

        // Ja esta na etapa certa: nada a fazer. E o caso comum — a maioria das
        // execucoes nao produz efeito nenhum, e e assim que tem que ser.
        if (etapaDevida <= empresa.getDunningEtapa()) {
            return false;
        }

        NivelAcesso nivel = regua.nivelDaEtapa(etapaDevida);
        log.info("Dunning: empresa {} etapa {} -> {} (acesso {})",
                empresaId, empresa.getDunningEtapa(), etapaDevida, nivel);

        avancarDunning(empresa, etapaDevida, nivel);
        return true;
    }

    /**
     * Aplica uma etapa da regua. Chamado pelo avancarSeNecessario e pela falha
     * imediata de pagamento.
     *
     * QUEM GARANTE A IDEMPOTENCIA E O `empresa.dunning_etapa`, nao a tabela de
     * eventos. A diferenca importa: dunning_etapa volta a zero quando o cliente
     * regulariza, entao ele identifica o CICLO atual de cobranca. O evento, nao
     * — ele e historico permanente.
     *
     * A primeira versao disto consultava (empresa, etapa, acao) em
     * dunning_eventos antes de aplicar. Parecia idempotencia e era um bug de
     * receita: cliente que atrasa, regulariza e atrasa de novo ja tinha o
     * evento da etapa 3 do ciclo anterior, o metodo saia pela porta dos fundos
     * e a regua NUNCA mais avancava para aquela empresa — ela deixava de ser
     * bloqueada em todos os atrasos seguintes.
     *
     * dunning_eventos e append-only: cada passagem pela regua deixa seu rastro.
     */
    void avancarDunning(Empresa empresa, int etapa, NivelAcesso nivel) {
        String acao = switch (nivel) {
            case SOMENTE_LEITURA -> "SOMENTE_LEITURA";
            case SUSPENSO -> "SUSPENSO";
            case NORMAL -> "EMAIL_ENVIADO";
        };

        registrarEvento(empresa, etapa, acao, regua.mensagemDaEtapa(etapa));

        empresa.setDunningEtapa(etapa);
        empresa.setDunningNotificadoEm(LocalDateTime.now());
        empresaRepository.save(empresa);

        if (regua.notifica(etapa)) {
            emailService.enviarAvisoDeCobranca(empresa, etapa, regua.mensagemDaEtapa(etapa));
        }
        if (nivel == NivelAcesso.SUSPENSO) {
            emailService.enviarSuspensao(empresa);
        }

        auditoriaService.registrar(empresa.getId(), "DUNNING_ETAPA_" + etapa,
                "empresa", empresa.getId(), null);
    }

    /** Trial expirado entra na mesma regua de quem teve o cartao recusado. */
    @Transactional
    public void expirarTrial(Long empresaId) {
        Empresa empresa = empresaRepository.findById(empresaId).orElseThrow();
        if (empresa.getAssinaturaStatus() != AssinaturaStatus.TRIALING) {
            return;
        }
        log.info("Trial expirado: empresa {}", empresaId);
        registrarFalhaDePagamento(empresaId);
    }

    // =================================================================
    // Consulta usada pelo interceptor e pelo front (banner)
    // =================================================================

    public NivelAcesso nivelDeAcesso(Empresa empresa) {
        return switch (empresa.getAssinaturaStatus()) {
            case ACTIVE, TRIALING -> NivelAcesso.NORMAL;
            case PAST_DUE -> regua.nivelDaEtapa(empresa.getDunningEtapa());
            case CANCELED -> NivelAcesso.SUSPENSO;
        };
    }

    /**
     * Versao por id, usada pelo interceptor de assinatura — que roda em TODA
     * requisicao. Por isso carrega so a projecao (status + etapa) em vez da
     * entidade inteira. Quando a base crescer, cachear esta consulta com TTL
     * curto e o proximo passo.
     *
     * Antes isto era um metodo default no EmpresaRepository chamando a regua de
     * forma estatica. Com a regua vindo de configuracao, ela e um bean — e
     * repositorio nao injeta bean. O lugar certo de logica de negocio e o
     * service, de todo jeito.
     */
    public NivelAcesso nivelDeAcessoDe(Long empresaId) {
        return empresaRepository.findSituacaoById(empresaId)
                .map(s -> switch (s.getAssinaturaStatus()) {
                    case ACTIVE, TRIALING -> NivelAcesso.NORMAL;
                    case PAST_DUE -> regua.nivelDaEtapa(s.getDunningEtapa());
                    case CANCELED -> NivelAcesso.SUSPENSO;
                })
                .orElse(NivelAcesso.NORMAL);
    }

    private void registrarEvento(Empresa empresa, int etapa, String acao, String detalhes) {
        var evento = new DunningEvento();
        evento.setEmpresaId(empresa.getId());
        evento.setEtapa(etapa);
        evento.setAcao(acao);
        evento.setDetalhes(detalhes);
        dunningEventoRepository.save(evento);
    }
}
