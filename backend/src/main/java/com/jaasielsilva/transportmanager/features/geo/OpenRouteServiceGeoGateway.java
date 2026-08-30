package com.jaasielsilva.transportmanager.features.geo;

import com.jaasielsilva.transportmanager.exception.GatewayGeoException;
import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.EstimativaRota;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Gateway OpenRouteService. Unica classe que conhece o JSON do ORS.
 *
 * Troca consciente de provedor (Google -> OpenRouteService): o Google Maps
 * exige cartao de credito/creditos; o ORS tem plano gratis sem cartao (base
 * OpenStreetMap). A regra de seguranca e a mesma: a API key vive so no backend,
 * por variavel de ambiente.
 *
 * A interface {@link GatewayGeo} nao muda; quem fala com o ORS e esta classe.
 * A Matrix do ORS NAO aceita endereco em texto — so coordenadas [lng, lat].
 * Entao o fluxo e de 2 passos:
 *   1. Geocodificar cada ponta (GET /pelias/v1/search?text=...) -> [lng, lat].
 *   2. Matrix driving-car com as coordenadas
 *      (POST /openrouteservice/v2/matrix/driving-car).
 * Sem resultado na geocodificacao, ou celula null na matriz = sem rota entre os
 * pontos — a chamada funcionou, nao e erro.
 *
 * O host novo e o api.heigit.org (o antigo api.openrouteservice.org foi
 * descontinuado em 24/08/2026). O caminho de cada servico muda: routing vira
 * /openrouteservice/v2/... e geocoding vira /pelias/v1/....
 */
@Component
public class OpenRouteServiceGeoGateway implements GatewayGeo {

    private static final Logger log = LoggerFactory.getLogger(OpenRouteServiceGeoGateway.class);

    private static final String BASE_URL = "https://api.heigit.org";
    private static final String CAMINHO_GEOCODE = "/pelias/v1/search";
    private static final String CAMINHO_MATRIX = "/openrouteservice/v2/matrix/driving-car";
    private static final String CAMINHO_DIRECTIONS = "/openrouteservice/v2/directions/driving-car/geojson";

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

        double[] origemCoordenadas = geocodificar(origem);
        double[] destinoCoordenadas = geocodificar(destino);
        if (origemCoordenadas == null || destinoCoordenadas == null) {
            // Endereco sem correspondencia no ORS = sem rota; a chamada funcionou.
            log.info("OpenRouteService sem geocodificacao: origem={} destino={}", origem, destino);
            return new EstimativaRota(null, null);
        }

        RespostaMatrix resposta;
        try {
            resposta = restClient.post()
                    .uri(CAMINHO_MATRIX)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(new RequisicaoMatrix(
                            new double[][]{origemCoordenadas, destinoCoordenadas},
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

        int distanciaKm = metrosParaKm(distancia);
        int tempoMin = segundosParaMinutos(duracao);
        log.info("Rota calculada: origem={} destino={} distanciaKm={} tempoMin={}",
                origem, destino, distanciaKm, tempoMin);
        return new EstimativaRota(distanciaKm, tempoMin);
    }

    @Override
    public List<double[]> tracar(String origem, String destino) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GatewayGeoException(
                    "OPENROUTESERVICE_API_KEY nao configurada. Configure a variavel de ambiente para usar o calculo de rota.");
        }

        double[] origemCoordenadas = geocodificar(origem);
        double[] destinoCoordenadas = geocodificar(destino);
        if (origemCoordenadas == null || destinoCoordenadas == null) {
            log.info("OpenRouteService sem geocodificacao para tracar rota: origem={} destino={}", origem, destino);
            return List.of();
        }

        RespostaDirections resposta;
        try {
            resposta = restClient.post()
                    .uri(CAMINHO_DIRECTIONS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(new RequisicaoDirections(new double[][]{origemCoordenadas, destinoCoordenadas}))
                    .retrieve()
                    .body(RespostaDirections.class);
        } catch (Exception e) {
            log.warn("Falha ao consultar Directions do OpenRouteService: origem={} destino={}", origem, destino, e);
            throw new GatewayGeoException("Nao foi possivel tracar a rota no OpenRouteService.", e);
        }

        if (resposta == null || resposta.features == null || resposta.features.isEmpty()) {
            log.info("OpenRouteService sem geometria de rota: origem={} destino={}", origem, destino);
            return List.of();
        }

        double[][] coordenadas = resposta.features.get(0).geometry.coordinates;
        if (coordenadas == null || coordenadas.length == 0) {
            return List.of();
        }

        // GeoJSON vem [lng, lat]; o Leaflet espera [lat, lng].
        List<double[]> geometria = new ArrayList<>(coordenadas.length);
        for (double[] par : coordenadas) {
            geometria.add(new double[]{par[1], par[0]});
        }
        log.info("Rota tracada: origem={} destino={} pontos={}", origem, destino, geometria.size());
        return geometria;
    }

    /**
     * Endereco -> [lng, lat] via geocoding do ORS. Sem correspondencia -> null
     * (rota inexistente, o front avisa "nao foi possivel tracar rota"). Falha
     * HTTP/timeout do ORS -> excecao (502).
     */
    private double[] geocodificar(String endereco) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(CAMINHO_GEOCODE)
                    .queryParam("text", endereco)
                    .build().encode().toUri();
            RespostaGeocoding resposta = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .body(RespostaGeocoding.class);
            if (resposta == null || resposta.features == null || resposta.features.isEmpty()) {
                log.info("OpenRouteService sem resultado de geocodificacao: {}", endereco);
                return null;
            }
            double[] coordenadas = resposta.features.get(0).geometry.coordinates;
            if (coordenadas == null || coordenadas.length < 2) {
                log.info("OpenRouteService sem coordenadas na geocodificacao: {}", endereco);
                return null;
            }
            return new double[]{coordenadas[0], coordenadas[1]};
        } catch (Exception e) {
            log.warn("Falha na geocodificacao OpenRouteService: {}", endereco, e);
            throw new GatewayGeoException("Nao foi possivel localizar o endereco.", e);
        }
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
    private record RequisicaoMatrix(double[][] locations, String[] metrics) {}
    private record RespostaMatrix(Double[][] distances, Double[][] durations) {}
    private record RespostaGeocoding(List<Feature> features) {
        private record Feature(Geometria geometry) {}
        private record Geometria(double[] coordinates) {}
    }

    /** Directions API — variante /geojson: coordinates e uma LineString [[lng, lat], ...]. */
    private record RequisicaoDirections(double[][] coordinates) {}
    private record RespostaDirections(List<FeatureLinha> features) {
        private record FeatureLinha(GeometriaLinha geometry) {}
        private record GeometriaLinha(double[][] coordinates) {}
    }
}
