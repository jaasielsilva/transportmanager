package com.jaasielsilva.transportmanager.features.billing;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Molde do kit — copiar para features/billing/ReguaDeDunning.java
 *
 * Dunning = a regua de cobranca de um pagamento que falhou. Nao e inadimplencia:
 * na maioria das vezes e cartao expirado ou limite estourado, e o cliente NAO
 * sabe que falhou. Bloquear na hora faz o cliente achar que o produto quebrou;
 * nao fazer nada faz voce parar de receber sem perceber.
 *
 * Esta classe e a REGRA — sem banco, sem e-mail, sem efeito colateral. Recebe
 * os prazos de DunningProperties e responde perguntas. Todo o comportamento se
 * testa aqui com JUnit, construindo a regua direto, sem subir contexto.
 * Os efeitos ficam no AssinaturaService e no DunningJob.
 *
 * Duas regras que nao se quebram:
 *   - login e pagamento NUNCA sao bloqueados (quem nao entra nao paga)
 *   - o cliente nunca perde dados; perde escrita, que e reversivel
 */
@Component
public class ReguaDeDunning {

    /** Empresa em dia. Nao e uma etapa da regua — e a ausencia dela. */
    public static final int ETAPA_EM_DIA = 0;

    private final List<DunningProperties.Etapa> etapas;

    /**
     * UM unico construtor, de proposito: com dois o Spring nao sabe qual usar
     * e a aplicacao nem sobe. Para teste, use a fabrica {@link #de(List)}.
     */
    public ReguaDeDunning(DunningProperties properties) {
        this.etapas = List.copyOf(properties.getEtapas());
    }

    /** Monta uma regua sem subir o Spring — usado nos testes. */
    public static ReguaDeDunning de(List<DunningProperties.Etapa> etapas) {
        var properties = new DunningProperties();
        properties.setEtapas(etapas);
        return new ReguaDeDunning(properties);
    }

    /**
     * Nivel de acesso resultante. Derivado, nunca digitado a mao — assim nao
     * existe empresa "suspensa por engano" por causa de um UPDATE manual.
     */
    public enum NivelAcesso {
        /** Uso normal. */
        NORMAL,
        /** Le tudo, nao grava nada. Os dados continuam visiveis — isso importa. */
        SOMENTE_LEITURA,
        /** So login, tela de regularizacao e billing. */
        SUSPENSO
    }

    /**
     * Etapa que a empresa DEVERIA estar, dado ha quantos dias esta em atraso.
     * 0 = em dia; 1 = primeira etapa configurada; N = ultima.
     */
    public int etapaPara(LocalDate pastDueDesde, LocalDate hoje) {
        if (pastDueDesde == null) {
            return ETAPA_EM_DIA;
        }
        long dias = ChronoUnit.DAYS.between(pastDueDesde, hoje);
        int etapa = ETAPA_EM_DIA;
        for (int i = 0; i < etapas.size(); i++) {
            if (dias >= etapas.get(i).dias()) {
                etapa = i + 1;
            }
        }
        return etapa;
    }

    public NivelAcesso nivelDaEtapa(int etapa) {
        if (etapa <= ETAPA_EM_DIA) {
            return NivelAcesso.NORMAL;
        }
        int indice = Math.min(etapa, etapas.size()) - 1;
        return etapas.get(indice).nivel();
    }

    /** true se a etapa dispara e-mail ao TENANT_ADMIN. */
    public boolean notifica(int etapa) {
        if (etapa <= ETAPA_EM_DIA || etapa > etapas.size()) {
            return false;
        }
        return etapas.get(etapa - 1).notificar();
    }

    /**
     * Mensagem exibida ao cliente. Tom importa: e uma falha tecnica de cobranca,
     * nao uma acusacao. O cliente precisa saber o que aconteceu, o que ainda
     * funciona e o que fazer.
     *
     * Derivada do NIVEL, e nao do numero da etapa: assim continua correta se
     * voce configurar tres etapas ou seis.
     */
    public String mensagemDaEtapa(int etapa) {
        if (etapa <= ETAPA_EM_DIA) {
            return "";
        }
        return switch (nivelDaEtapa(etapa)) {
            case NORMAL -> "Nao conseguimos processar seu pagamento. Verifique os dados do cartao "
                    + "— seu acesso segue normal.";
            case SOMENTE_LEITURA -> "Sua conta esta em modo somente leitura ate a regularizacao. "
                    + "Seus dados estao seguros.";
            case SUSPENSO -> "Sua conta esta suspensa. Regularize o pagamento para reativar "
                    + "— nenhum dado foi excluido.";
        };
    }

    /** Quantidade de etapas configuradas. */
    public int totalDeEtapas() {
        return etapas.size();
    }
}
