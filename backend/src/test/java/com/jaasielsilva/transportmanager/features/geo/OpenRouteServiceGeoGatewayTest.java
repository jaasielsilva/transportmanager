package com.jaasielsilva.transportmanager.features.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jaasielsilva.transportmanager.exception.GatewayGeoException;
import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.EstimativaRota;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Teste HTTP do gateway OpenRouteService (host api.heigit.org): geocodifica cada
 * ponta (GET /pelias/v1/search) e depois chama a Matrix (POST
 * /openrouteservice/v2/matrix/driving-car). A key fake nunca sai do teste; nada
 * cai na rede.
 */
class OpenRouteServiceGeoGatewayTest {

    private static final String CHAVE = "chave-de-teste";

    private static final double LNG_SAO_PAULO = -46.6333;
    private static final double LAT_SAO_PAULO = -23.5505;
    private static final double LNG_CAMPINAS = -47.0608;
    private static final double LAT_CAMPINAS = -22.9099;

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
    @DisplayName("geocode das duas pontas + matriz vira km e minutos")
    void devolveEstimativaQuandoRespondeComMatriz() {
        server.expect(geocodificacaoDe("Sao Paulo, SP, Brasil"))
                .andRespond(withSuccess(geojsonDe(LNG_SAO_PAULO, LAT_SAO_PAULO), MediaType.APPLICATION_JSON));
        server.expect(geocodificacaoDe("Campinas, SP, Brasil"))
                .andRespond(withSuccess(geojsonDe(LNG_CAMPINAS, LAT_CAMPINAS), MediaType.APPLICATION_JSON));
        server.expect(matrizCom(LNG_SAO_PAULO, LAT_SAO_PAULO, LNG_CAMPINAS, LAT_CAMPINAS))
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
    @DisplayName("endereco sem correspondencia na geocodificacao = sem rota, nao erro")
    void enderecoSemGeocodificacaoDevolveNulo() {
        server.expect(geocodificacaoDe("Lugar Inexistente"))
                .andRespond(withSuccess("{\"features\": []}", MediaType.APPLICATION_JSON));
        server.expect(geocodificacaoDe("Outro Lugar"))
                .andRespond(withSuccess("{\"features\": []}", MediaType.APPLICATION_JSON));

        EstimativaRota estimativa = gateway.estimar("Lugar Inexistente", "Outro Lugar");

        assertThat(estimativa.distanciaKm()).isNull();
        assertThat(estimativa.tempoEstimadoMinutos()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("celula null na matriz = sem rota, nao erro")
    void semRotaDevolveNuloEmVezDeErro() {
        server.expect(geocodificacaoDe("A"))
                .andRespond(withSuccess(geojsonDe(0, 0), MediaType.APPLICATION_JSON));
        server.expect(geocodificacaoDe("B"))
                .andRespond(withSuccess(geojsonDe(0, 0), MediaType.APPLICATION_JSON));
        server.expect(matrizCom(0, 0, 0, 0))
                .andRespond(withSuccess("""
                        {
                          "distances": [[0, null], [null, 0]],
                          "durations": [[0, null], [null, 0]]
                        }
                        """, MediaType.APPLICATION_JSON));

        EstimativaRota estimativa = gateway.estimar("A", "B");

        assertThat(estimativa.distanciaKm()).isNull();
        assertThat(estimativa.tempoEstimadoMinutos()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("falha HTTP (5xx) na geocodificacao vira GatewayGeoException (502)")
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

    /** GET /pelias/v1/search?text=<endereco> com Authorization Bearer. */
    private static RequestMatcher geocodificacaoDe(String endereco) {
        return request -> {
            assertThat(request.getMethod()).isEqualTo(GET);
            assertThat(request.getURI().getPath()).isEqualTo("/pelias/v1/search");
            assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer " + CHAVE);

            // getQueryParams() devolve valor percent-encoded; decodifica antes de comparar.
            String valor = UriComponentsBuilder.fromUri(request.getURI())
                    .build().getQueryParams().getFirst("text");
            assertThat(URLDecoder.decode(valor, StandardCharsets.UTF_8)).isEqualTo(endereco);
        };
    }

    /** POST /openrouteservice/v2/matrix/driving-car com as coordenadas geocodificadas. */
    private static RequestMatcher matrizCom(double lngOrigem, double latOrigem,
                                            double lngDestino, double latDestino) {
        return request -> {
            assertThat(request.getMethod()).isEqualTo(POST);
            assertThat(request.getURI().getPath()).isEqualTo("/openrouteservice/v2/matrix/driving-car");
            assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer " + CHAVE);

            String corpo = ((MockClientHttpRequest) request).getBodyAsString();
            assertThat(corpo).contains(String.valueOf(lngOrigem));
            assertThat(corpo).contains(String.valueOf(latOrigem));
            assertThat(corpo).contains(String.valueOf(lngDestino));
            assertThat(corpo).contains(String.valueOf(latDestino));
            assertThat(corpo).contains("\"distance\"");
            assertThat(corpo).contains("\"duration\"");
        };
    }

    /** GeoJSON de resposta do pelias (mesma forma do ORS: features[].geometry.coordinates). */
    private static String geojsonDe(double lng, double lat) {
        return """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": { "type": "Point", "coordinates": [%s, %s] },
                      "properties": { "name": "local de teste" }
                    }
                  ]
                }
                """.formatted(lng, lat);
    }
}
