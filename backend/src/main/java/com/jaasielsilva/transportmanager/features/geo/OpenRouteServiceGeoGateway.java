package com.jaasielsilva.transportmanager.features.geo;

import com.jaasielsilva.transportmanager.exception.GatewayGeoException;
import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.EstimativaRota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Gateway OpenRouteService (Matrix API). Unica classe que conhece o JSON do ORS.
 *
 * Troca consciente de provedor (Google -> OpenRouteService): o Google Maps
 * exige cartao de credito/creditos; o ORS tem plano gratis sem cartao, com a
 * API de Matrix incluida (~2.500-8.000 req/dia, base OpenStreetMap). A regra de
 * seguranca e a mesma: a API key vive so no backend, por variavel de ambiente.
 *
 * A interface {@link GatewayGeo} nao muda; quem fala com o ORS e esta classe.
 * Endpoint: POST /v2/matrix/driving-car com locations (origem/destino) e
 * metrics [distance, duration]. Celula null na matriz = sem rota entre os
 * pontos — nao e erro, e a chamada funcionou.
 */
@Component
public class OpenRouteServiceGeoGateway implements GatewayGeo {

    private static final Logger log = LoggerFactory.getLogger(OpenRouteServiceGeoGateway.class);

    private static final String BASE_URL = "https://api.openrouteservice.org";
    private static final String CAMINHO = "/v2/matrix/driving-car";

    private final String apiKey;
    private final RestClient restClient;

    public OpenRouteServiceGeoGateway(RestClient.Builder builder,
                                      @Value("${app.openrouteservice.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public EstimativaRota estimar(String origem, String destino) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GatewayGeoException(
                    "OPENROUTESERVICE_API_KEY nao configurada. Configure a variavel de ambiente para usar o calculo de rota.");
        }

        RespostaMatrix resposta;
        try {
            resposta = restClient.post()
                    .uri(CAMINHO)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(new RequisicaoMatrix(new String[]{origem, destino},
                            new String[]{"distance", "duration"}))
                    .retrieve()
                    .body(RespostaMatrix.class);
        } catch (Exception e) {
            // HTTP != 2xx, timeout, conexao recusada... tudo vira 502.
            log.warn("Falha ao consultar OpenRouteService: origem={} destino={}", origem, destino, e);
            throw new GatewayGeoException("Nao foi possivel consultar o OpenRouteService.", e);
        }

        if (resposta == null || resposta.distances == null || resposta.durations == null
                || resposta.distances.length == 0 || resposta.durations.length == 0) {
            throw new GatewayGeoException(
                    "OpenRouteService retornou resposta inesperada: " + (resposta == null ? "sem resposta" : "matriz vazia"));
        }

        Double distancia = valorDaMatriz(resposta.distances);
        Double duracao = valorDaMatriz(resposta.durations);
        if (distancia == null || duracao == null) {
            // Celula null = sem rota entre os pontos; a chamada funcionou.
            log.info("OpenRouteService sem rota: origem={} destino={}", origem, destino);
            return new EstimativaRota(null, null);
        }

        return new EstimativaRota(
                metrosParaKm(distancia),
                segundosParaMinutos(duracao));
    }

    /**
     * A matriz tem origem/destino na ordem do request; a celula [0][1] e a rota
     * origem -> destino. Null no indice = sem caminho.
     */
    private Double valorDaMatriz(Double[][] matriz) {
        if (matriz[0] == null || matriz[0].length < 2) {
            return null;
        }
        return matriz[0][1];
    }

    /** ORS entrega metros; o form trabalha com km inteiros. */
    private int metrosParaKm(double metros) {
        return Math.round((float) metros / 1000f);
    }

    /** Segundos -> minutos, arredondado para cima (nunca "0 min" para 30s). */
    private int segundosParaMinutos(double segundos) {
        return (int) Math.ceil(segundos / 60d);
    }

    /** Estruturas do JSON do ORS — so o que a gente usa. */
    private record RequisicaoMatrix(String[] locations, String[] metrics) {}
    private record RespostaMatrix(Double[][] distances, Double[][] durations) {}
}
