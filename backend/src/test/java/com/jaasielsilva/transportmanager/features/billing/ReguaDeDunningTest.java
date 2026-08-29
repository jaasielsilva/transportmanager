package com.jaasielsilva.transportmanager.features.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaasielsilva.transportmanager.features.billing.DunningProperties.Etapa;
import com.jaasielsilva.transportmanager.features.billing.ReguaDeDunning.NivelAcesso;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Molde do kit — copiar para src/test/java/.../features/billing/ReguaDeDunningTest.java
 *
 * A regra do ciclo comercial se testa sem banco, sem Spring, sem mock. Foi para
 * isso que ReguaDeDunning ficou sem efeito colateral: a regra que decide se um
 * cliente perde acesso e cara demais para ser validada so em producao.
 */
class ReguaDeDunningTest {

    private static final LocalDate FALHA = LocalDate.of(2026, 8, 1);

    /** Mesma configuracao do application.yml — o padrao do kit. */
    private final ReguaDeDunning regua = ReguaDeDunning.de(List.of(
            new Etapa(0, NivelAcesso.NORMAL, true),
            new Etapa(3, NivelAcesso.NORMAL, true),
            new Etapa(7, NivelAcesso.SOMENTE_LEITURA, true),
            new Etapa(15, NivelAcesso.SUSPENSO, false)));

    @ParameterizedTest(name = "D+{0} -> etapa {1}")
    @CsvSource({
            " 0, 1",
            " 1, 1",
            " 2, 1",
            " 3, 2",
            " 6, 2",
            " 7, 3",
            "14, 3",
            "15, 4",
            "90, 4",     // nao passa da ultima etapa configurada
    })
    void calculaAEtapaPelosDiasEmAtraso(int dias, int etapaEsperada) {
        assertThat(regua.etapaPara(FALHA, FALHA.plusDays(dias))).isEqualTo(etapaEsperada);
    }

    @Test
    @DisplayName("empresa em dia nao entra na regua")
    void semAtrasoNaoEntraNaRegua() {
        assertThat(regua.etapaPara(null, FALHA)).isEqualTo(ReguaDeDunning.ETAPA_EM_DIA);
    }

    @Test
    @DisplayName("os 7 primeiros dias nao tiram acesso — o cliente ainda nem sabe que falhou")
    void primeiraSemanaMantemAcessoNormal() {
        assertThat(regua.nivelDaEtapa(1)).isEqualTo(NivelAcesso.NORMAL);
        assertThat(regua.nivelDaEtapa(2)).isEqualTo(NivelAcesso.NORMAL);
    }

    @Test
    @DisplayName("D+7 tira a escrita, D+15 suspende")
    void escalaDeBloqueio() {
        assertThat(regua.nivelDaEtapa(3)).isEqualTo(NivelAcesso.SOMENTE_LEITURA);
        assertThat(regua.nivelDaEtapa(4)).isEqualTo(NivelAcesso.SUSPENSO);
    }

    @Test
    @DisplayName("suspensao nao notifica — a essa altura o e-mail ja foi enviado 3 vezes")
    void notificacaoSoNasTresPrimeirasEtapas() {
        assertThat(regua.notifica(1)).isTrue();
        assertThat(regua.notifica(3)).isTrue();
        assertThat(regua.notifica(4)).isFalse();
        assertThat(regua.notifica(ReguaDeDunning.ETAPA_EM_DIA)).isFalse();
    }

    @Test
    @DisplayName("toda etapa da regua tem mensagem para o cliente")
    void mensagemDefinidaEmTodaEtapa() {
        for (int etapa = 1; etapa <= regua.totalDeEtapas(); etapa++) {
            assertThat(regua.mensagemDaEtapa(etapa)).isNotBlank();
        }
        assertThat(regua.mensagemDaEtapa(ReguaDeDunning.ETAPA_EM_DIA)).isEmpty();
    }

    @Test
    @DisplayName("cliente que atrasa, regulariza e atrasa de novo percorre a regua inteira outra vez")
    void reincidenciaPercorreAReguaDeNovo() {
        // Este teste existe por causa de um bug real: a idempotencia era feita
        // consultando (empresa, etapa, acao) na tabela de eventos. Como o evento
        // do ciclo anterior continuava la, o cliente reincidente nunca mais era
        // bloqueado — falhava o pagamento e seguia usando o sistema.
        //
        // A regua e uma funcao do tempo desde o atraso ATUAL. Um novo atraso e
        // um novo ponto de partida, e tem que produzir exatamente o mesmo
        // percurso do primeiro.
        LocalDate primeiroAtraso = LocalDate.of(2026, 1, 10);
        LocalDate segundoAtraso = LocalDate.of(2026, 6, 20);

        for (int dias : new int[] {0, 3, 7, 15}) {
            assertThat(regua.etapaPara(segundoAtraso, segundoAtraso.plusDays(dias)))
                    .as("D+%d do segundo atraso deve dar a mesma etapa do primeiro", dias)
                    .isEqualTo(regua.etapaPara(primeiroAtraso, primeiroAtraso.plusDays(dias)));
        }
    }

    @Test
    @DisplayName("regularizar zera a regua — e o dunning_etapa que identifica o ciclo")
    void regularizacaoZeraOCiclo() {
        assertThat(regua.etapaPara(null, FALHA.plusDays(30)))
                .isEqualTo(ReguaDeDunning.ETAPA_EM_DIA);
        assertThat(regua.nivelDaEtapa(ReguaDeDunning.ETAPA_EM_DIA))
                .isEqualTo(NivelAcesso.NORMAL);
    }

    // =================================================================
    // Configuracao diferente da padrao
    // =================================================================

    @Test
    @DisplayName("regua mais curta: quem configura 2 etapas tem 2 etapas, sem surpresa")
    void reguaConfiguravelComOutrosPrazos() {
        var curta = ReguaDeDunning.de(List.of(
                new Etapa(0, NivelAcesso.NORMAL, true),
                new Etapa(5, NivelAcesso.SUSPENSO, true)));

        assertThat(curta.etapaPara(FALHA, FALHA.plusDays(0))).isEqualTo(1);
        assertThat(curta.etapaPara(FALHA, FALHA.plusDays(4))).isEqualTo(1);
        assertThat(curta.etapaPara(FALHA, FALHA.plusDays(5))).isEqualTo(2);
        assertThat(curta.etapaPara(FALHA, FALHA.plusDays(60))).isEqualTo(2);
        assertThat(curta.nivelDaEtapa(2)).isEqualTo(NivelAcesso.SUSPENSO);
        // A mensagem segue o NIVEL, nao o numero da etapa — por isso continua
        // correta com uma regua de tamanho diferente.
        assertThat(curta.mensagemDaEtapa(2)).contains("suspensa");
    }

    @Test
    @DisplayName("regua mais tolerante: nunca suspende, so tira a escrita")
    void reguaSemSuspensao() {
        var tolerante = ReguaDeDunning.de(List.of(
                new Etapa(0, NivelAcesso.NORMAL, true),
                new Etapa(10, NivelAcesso.SOMENTE_LEITURA, true)));

        assertThat(tolerante.nivelDaEtapa(tolerante.totalDeEtapas()))
                .isEqualTo(NivelAcesso.SOMENTE_LEITURA);
        assertThat(tolerante.etapaPara(FALHA, FALHA.plusDays(365)))
                .isEqualTo(2);
    }
}
