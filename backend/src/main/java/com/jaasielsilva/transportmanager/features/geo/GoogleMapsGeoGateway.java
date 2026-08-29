package com.jaasielsilva.transportmanager.features.geo;

import com.jaasielsilva.transportmanager.exception.GatewayGeoException;
import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.EstimativaRota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Gateway Google Distance Matrix. Unica classe que conhece o JSON do Google.
 *
 * O RestClient do Spring ja vem no classpath (spring-web) — zero dependencia
 * nova no pom. A API key vem de variavel de ambiente e nunca sai do backend.
 *
 * O builder injetado e o auto-configurado do Spring; os timeouts vem das
 * propriedades spring.http.client.* do application.yml. Nao se mexe no request
 * factory aqui de proposito: e isso que permite ao teste usar o
 * MockRestServiceServer ligado ao mesmo builder.
 */
@Component
public class GoogleMapsGeoGateway implements GatewayGeo {

    private static final Logger log = LoggerFactory.getLogger(GoogleMapsGeoGateway.class);

    private static final String BASE_URL = "https://maps.googleapis.com";
    private static final String CAMINHO = "/maps/api/distancematrix/json";
    private static final String MODO_RODOVIARIO = "driving";

    private final String apiKey;
    private final RestClient restClient;

    public GoogleMapsGeoGateway(RestClient.Builder builder,
                                @Value("${app.google-maps.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public EstimativaRota estimar(String origem, String destino) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GatewayGeoException(
                    "GOOGLE_MAPS_API_KEY nao configurada. Configure a variavel de ambiente para usar o calculo de rota.");
        }

        RespostaDistanceMatrix resposta;
        try {
            resposta = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(CAMINHO)
                            .queryParam("origins", origem)
                            .queryParam("destinations", destino)
                            .queryParam("mode", MODO_RODOVIARIO)
                            .queryParam("language", "pt-BR")
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(RespostaDistanceMatrix.class);
        } catch (Exception e) {
            // HTTP != 2xx, timeout, conexao recusada... tudo vira 502.
            log.warn("Falha ao consultar Distance Matrix: origem={} destino={}", origem, destino, e);
            throw new GatewayGeoException("Nao foi possivel consultar o Google Maps.", e);
        }

        if (resposta == null || !"OK".equals(resposta.status)) {
            throw new GatewayGeoException(
                    "Google Maps retornou status inesperado: " + (resposta == null ? "sem resposta" : resposta.status));
        }

        Elemento elemento = primeiroElemento(resposta);
        if (elemento == null || !"OK".equals(elemento.status)) {
            // ZERO_RESULTS / NOT_FOUND: nao ha rota, mas a chamada funcionou.
            log.info("Google Maps sem rota: origem={} destino={} status={}", origem, destino,
                    elemento == null ? "sem elemento" : elemento.status);
            return new EstimativaRota(null, null);
        }

        return new EstimativaRota(
                metrosParaKm(elemento.distance.value),
                segundosParaMinutos(elemento.duration.value));
    }

    /** Localiza o primeiro elemento da primeira linha; tolerante a shape vazio. */
    private Elemento primeiroElemento(RespostaDistanceMatrix resposta) {
        if (resposta.rows == null || resposta.rows.length == 0) {
            return null;
        }
        Linha linha = resposta.rows[0];
        if (linha.elements == null || linha.elements.length == 0) {
            return null;
        }
        return linha.elements[0];
    }

    /** Google entrega metros inteiros; o form trabalha com km inteiros. */
    private int metrosParaKm(long metros) {
        return Math.round(metros / 1000f);
    }

    /** Segundos -> minutos, arredondado para cima (nunca "0 min" para 30s). */
    private int segundosParaMinutos(long segundos) {
        return (int) Math.ceil(segundos / 60d);
    }

    /** Estruturas do JSON da Distance Matrix — so o que a gente usa. */
    private record RespostaDistanceMatrix(String status, Linha[] rows) {}
    private record Linha(Elemento[] elements) {}
    private record Elemento(String status, Valor distance, Valor duration) {}
    private record Valor(long value) {}
}
