package com.jaasielsilva.transportmanager.features.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jaasielsilva.transportmanager.exception.GatewayGeoException;
import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.EstimativaRota;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Primeiro teste HTTP do projeto: o gateway fala com o Google via RestClient, e
 * o MockRestServiceServer (spring-test, ja no classpath) intercepta a chamada —
 * sem rede, sem key real. O que se prova aqui e o contrato da API: URL, query
 * params e o parse do JSON especifico do Google.
 */
class GoogleMapsGeoGatewayTest {

    private static final String CHAVE = "chave-de-teste";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private GoogleMapsGeoGateway gateway;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new GoogleMapsGeoGateway(builder, CHAVE);
    }

    @Test
    @DisplayName("resposta OK vira distancia em km e tempo em minutos")
    void devolveEstimativaQuandoGoogleRespondeOk() {
        server.expect(requisicaoComParams("Sao Paulo, Brasil", "Campinas, Brasil", CHAVE))
                .andRespond(withSuccess("""
                        {
                          "status": "OK",
                          "rows": [
                            { "elements": [
                              {
                                "status": "OK",
                                "distance": { "value": 96200 },
                                "duration": { "value": 5100 }
                              }
                            ] }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        EstimativaRota estimativa = gateway.estimar("Sao Paulo, Brasil", "Campinas, Brasil");

        assertThat(estimativa.distanciaKm()).isEqualTo(96);        // 96.200 m -> 96 km
        assertThat(estimativa.tempoEstimadoMinutos()).isEqualTo(85); // 5.100 s -> 85 min
        server.verify();
    }

    @Test
    @DisplayName("ZERO_RESULTS nao e erro: devolve estimativa nula")
    void semRotaDevolveNuloEmVezDeErro() {
        server.expect(anything()).andRespond(withSuccess("""
                {
                  "status": "OK",
                  "rows": [ { "elements": [ { "status": "ZERO_RESULTS" } ] } ]
                }
                """, MediaType.APPLICATION_JSON));

        EstimativaRota estimativa = gateway.estimar("Ilha Sem Saida", "Ilha Sem Chegada");

        assertThat(estimativa.distanciaKm()).isNull();
        assertThat(estimativa.tempoEstimadoMinutos()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("status de erro do Google vira GatewayGeoException (502)")
    void statusDeErroDoGoogleLancaExcecao() {
        server.expect(anything()).andRespond(withSuccess("""
                { "status": "OVER_QUERY_LIMIT", "rows": [] }
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.estimar("A", "B"))
                .isInstanceOf(GatewayGeoException.class);
        server.verify();
    }

    @Test
    @DisplayName("falha HTTP (5xx) vira GatewayGeoException")
    void falhaHttpLancaExcecao() {
        server.expect(anything()).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.estimar("A", "B"))
                .isInstanceOf(GatewayGeoException.class);
        server.verify();
    }

    @Test
    @DisplayName("API key vazia falha cedo, sem chamar o Google")
    void chaveVaziaLancaExcecaoSemChamarGoogle() {
        GoogleMapsGeoGateway semChave = new GoogleMapsGeoGateway(RestClient.builder(), "");

        assertThatThrownBy(() -> semChave.estimar("A", "B"))
                .isInstanceOf(GatewayGeoException.class)
                .hasMessageContaining("GOOGLE_MAPS_API_KEY");
    }

    /** Confere caminho e query params da chamada ao Google. */
    private static RequestMatcher requisicaoComParams(String origins, String destinations, String key) {
        return request -> {
            var uri = request.getURI();
            assertThat(uri.getPath()).isEqualTo("/maps/api/distancematrix/json");

            // O URI chega percent-encoded; decodifica para comparar com o valor original.
            Map<String, String> params = UriComponentsBuilder.fromUri(uri)
                    .build().getQueryParams().toSingleValueMap();
            params.replaceAll((k, v) -> v == null ? null : URLDecoder.decode(v, StandardCharsets.UTF_8));
            assertThat(params).containsEntry("origins", origins);
            assertThat(params).containsEntry("destinations", destinations);
            assertThat(params).containsEntry("mode", "driving");
            assertThat(params).containsEntry("key", key);
        };
    }
}
