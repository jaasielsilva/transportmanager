package com.jaasielsilva.transportmanager.features.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jaasielsilva.transportmanager.exception.GatewayGeoException;
import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.EstimativaRota;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

/**
 * Teste HTTP do gateway OpenRouteService (Matrix API): valida o contrato — POST
 * no caminho certo, Authorization Bearer com a key, body com locations/metrics
 * e o parse da matriz. A key fake nunca sai do teste; nada cai na rede.
 */
class OpenRouteServiceGeoGatewayTest {

    private static final String CHAVE = "chave-de-teste";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private OpenRouteServiceGeoGateway gateway;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new OpenRouteServiceGeoGateway(builder, CHAVE);
    }

    @Test
    @DisplayName("matriz com distancia/duracao vira km e minutos")
    void devolveEstimativaQuandoRespondeComMatriz() {
        server.expect(requisicaoDe("Sao Paulo, SP, Brasil", "Campinas, SP, Brasil", CHAVE))
                .andRespond(withSuccess("""
                        {
                          "distances": [[0, 96200], [96200, 0]],
                          "durations": [[0, 5100], [5100, 0]]
                        }
                        """, MediaType.APPLICATION_JSON));

        EstimativaRota estimativa = gateway.estimar("Sao Paulo, SP, Brasil", "Campinas, SP, Brasil");

        assertThat(estimativa.distanciaKm()).isEqualTo(96);          // 96.200 m -> 96 km
        assertThat(estimativa.tempoEstimadoMinutos()).isEqualTo(85); // 5.100 s -> 85 min
        server.verify();
    }

    @Test
    @DisplayName("celula null na matriz = sem rota, nao erro")
    void semRotaDevolveNuloEmVezDeErro() {
        server.expect(anything()).andRespond(withSuccess("""
                {
                  "distances": [[0, null], [null, 0]],
                  "durations": [[0, null], [null, 0]]
                }
                """, MediaType.APPLICATION_JSON));

        EstimativaRota estimativa = gateway.estimar("Ilha Sem Saida", "Ilha Sem Chegada");

        assertThat(estimativa.distanciaKm()).isNull();
        assertThat(estimativa.tempoEstimadoMinutos()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("falha HTTP (5xx) vira GatewayGeoException (502)")
    void falhaHttpLancaExcecao() {
        server.expect(anything()).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.estimar("A", "B"))
                .isInstanceOf(GatewayGeoException.class);
        server.verify();
    }

    @Test
    @DisplayName("API key vazia falha cedo, sem chamar o ORS")
    void chaveVaziaLancaExcecaoSemChamar() {
        OpenRouteServiceGeoGateway semChave = new OpenRouteServiceGeoGateway(RestClient.builder(), "");

        assertThatThrownBy(() -> semChave.estimar("A", "B"))
                .isInstanceOf(GatewayGeoException.class)
                .hasMessageContaining("OPENROUTESERVICE_API_KEY");
    }

    /** Confere caminho, Authorization Bearer e corpo do request ao ORS. */
    private static RequestMatcher requisicaoDe(String origem, String destino, String key) {
        return request -> {
            var uri = request.getURI();
            assertThat(uri.getPath()).isEqualTo("/v2/matrix/driving-car");
            assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer " + key);

            String corpo = ((MockClientHttpRequest) request).getBodyAsString();
            assertThat(corpo).contains(origem);
            assertThat(corpo).contains(destino);
            assertThat(corpo).contains("\"distance\"");
            assertThat(corpo).contains("\"duration\"");
        };
    }
}
