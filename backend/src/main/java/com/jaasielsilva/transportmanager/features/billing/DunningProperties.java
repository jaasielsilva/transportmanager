package com.jaasielsilva.transportmanager.features.billing;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Molde do kit — features/billing/DunningProperties.java
 *
 * Prazos da regua de cobranca em configuracao (app.dunning), nao no codigo.
 * Voce VAI querer ajustar: descobrir que 7 dias e agressivo demais para o seu
 * mercado e precisar de deploy para mudar um numero e ruim.
 *
 * O que fica configuravel: os prazos, o nivel de acesso de cada etapa e se ela
 * notifica. O que NAO fica: regra diferente por cliente. No momento em que cada
 * tenant tem a propria regua, ninguem consegue mais responder "quando meus
 * clientes sao suspensos?" — a resposta vira "depende", e suporte e teste ficam
 * impossiveis. Excecao individual se resolve com o botao "prorrogar" do painel,
 * que e auditado.
 */
@Component
@ConfigurationProperties(prefix = "app.dunning")
public class DunningProperties {

    private static final Logger log = LoggerFactory.getLogger(DunningProperties.class);

    private List<Etapa> etapas = List.of();

    /**
     * Uma etapa da regua.
     *
     * @param dias      dias em atraso a partir dos quais a etapa vale
     * @param nivel     acesso resultante (NORMAL, SOMENTE_LEITURA, SUSPENSO)
     * @param notificar se dispara e-mail ao TENANT_ADMIN
     */
    public record Etapa(int dias, ReguaDeDunning.NivelAcesso nivel, boolean notificar) {}

    /**
     * Configuracao invalida derruba a aplicacao na subida, de proposito.
     *
     * Uma regua fora de ordem nao "funciona mal": ela suspende cliente que
     * deveria estar em dia. Melhor nao subir do que subir errado — e o erro
     * aparece no deploy, nao no cliente.
     */
    @PostConstruct
    void validar() {
        if (etapas.isEmpty()) {
            throw new IllegalStateException(
                    "app.dunning.etapas nao configurado. Sem regua, cliente inadimplente nunca e cobrado.");
        }

        for (int i = 0; i < etapas.size(); i++) {
            Etapa e = etapas.get(i);
            if (e.dias() < 0) {
                throw new IllegalStateException(
                        "app.dunning.etapas[" + i + "].dias nao pode ser negativo: " + e.dias());
            }
            if (e.nivel() == null) {
                throw new IllegalStateException("app.dunning.etapas[" + i + "].nivel e obrigatorio");
            }
            if (i > 0 && e.dias() <= etapas.get(i - 1).dias()) {
                throw new IllegalStateException(
                        "app.dunning.etapas precisa estar em ordem CRESCENTE de dias. "
                                + "Etapa " + i + " (" + e.dias() + "d) nao vem depois da anterior ("
                                + etapas.get(i - 1).dias() + "d).");
            }
        }

        log.info("Regua de dunning: {}", etapas.stream()
                .map(e -> "D+" + e.dias() + "->" + e.nivel())
                .toList());
    }

    public List<Etapa> getEtapas() {
        return etapas;
    }

    public void setEtapas(List<Etapa> etapas) {
        this.etapas = etapas;
    }
}
