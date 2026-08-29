package com.jaasielsilva.transportmanager.features.geo.dto;

/**
 * DTOs do modulo geo (integracao com provedor de rotas — hoje Google Distance
 * Matrix). O gateway e a unica camada que conhece o JSON do provedor; aqui so
 * trafega o que o form de carga precisa.
 */
public final class GeoDtos {

    private GeoDtos() {}

    /**
     * Estimativa de rota entre origem e destino. Campos nullable de proposito:
     * o Google pode nao achar rota (ZERO_RESULTS) — o form mostra um aviso em
     * vez de quebrar.
     */
    public record EstimativaRota(Integer distanciaKm, Integer tempoEstimadoMinutos) {}
}
