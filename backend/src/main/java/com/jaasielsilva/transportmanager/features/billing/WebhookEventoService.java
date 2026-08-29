package com.jaasielsilva.transportmanager.features.billing;

import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.features.billing.GatewayBilling.EventoBilling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Molde do kit — features/billing/WebhookEventoService.java
 *
 * Registro e processamento dos eventos do gateway.
 *
 * Existe como bean separado por uma razao concreta: o controller chamava um
 * metodo @Transactional dele mesmo, e chamada interna nao passa pelo proxy do
 * Spring — a transacao nunca era aberta. Com o service, a chamada e externa e
 * a anotacao vale.
 */
@Service
public class WebhookEventoService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventoService.class);

    private static final int DIAS_DE_RETENCAO = 30;

    private final WebhookEventoRepository repository;
    private final AssinaturaService assinaturaService;
    private final GatewayBilling gateway;

    public WebhookEventoService(WebhookEventoRepository repository,
                                AssinaturaService assinaturaService,
                                GatewayBilling gateway) {
        this.repository = repository;
        this.assinaturaService = assinaturaService;
        this.gateway = gateway;
    }

    /**
     * Grava o evento recebido.
     *
     * REQUIRES_NEW: o registro precisa sobreviver mesmo que o processamento
     * falhe logo depois — e o que permite reprocessar pelo painel em vez de
     * corrigir na mao no banco de producao.
     *
     * @return false se o evento ja era conhecido (reentrega do gateway).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarSeNovo(EventoBilling evento, String payload) {
        if (repository.findByEventoId(evento.eventoId()).isPresent()) {
            return false;
        }
        var registro = new WebhookEvento();
        registro.setEventoId(evento.eventoId());
        registro.setTipo(evento.tipo().name());
        registro.setPayload(payload);
        repository.save(registro);
        return true;
    }

    /** Aplica o efeito do evento. Sem token = sem tenant. */
    @Transactional
    public void processar(EventoBilling evento) {
        TenantContext.semFiltroDeTenant(() -> {
            Long empresaId = evento.empresaId();
            switch (evento.tipo()) {
                case PAGAMENTO_CONFIRMADO -> assinaturaService.registrarPagamentoConfirmado(empresaId);
                case PAGAMENTO_FALHOU     -> assinaturaService.registrarFalhaDePagamento(empresaId);
                case ASSINATURA_CANCELADA -> assinaturaService.registrarCancelamento(empresaId, DIAS_DE_RETENCAO);
                case IGNORADO             -> log.debug("Evento {} sem efeito", evento.eventoId());
            }
            return null;
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarProcessado(String eventoId) {
        repository.marcarProcessado(eventoId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarErro(String eventoId, String erro) {
        // Mensagem truncada: stack trace inteiro nao cabe e nao ajuda na tela
        repository.marcarErro(eventoId, erro == null ? "erro desconhecido"
                : erro.substring(0, Math.min(erro.length(), 500)));
    }

    /**
     * Reprocessa um evento que falhou, a partir do payload guardado.
     * Acionado pelo painel da plataforma.
     */
    @Transactional
    public void reprocessar(String eventoId) {
        WebhookEvento registro = repository.findByEventoId(eventoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento nao encontrado."));

        EventoBilling evento = gateway.interpretar(registro.getPayload());
        processar(evento);
        repository.marcarProcessado(eventoId);
        log.info("Webhook {} reprocessado com sucesso", eventoId);
    }
}
