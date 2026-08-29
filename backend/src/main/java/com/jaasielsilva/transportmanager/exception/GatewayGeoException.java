package com.jaasielsilva.transportmanager.exception;

/**
 * Falha ao falar com o provedor de geolocalizacao (Google Distance Matrix).
 *
 * Vira 502 BAD_GATEWAY no GlobalExceptionHandler: o upstream que falhou foi o
 * Google, nao a nossa aplicacao — confundir isso com um 500 nosso so cria
 * alerta falso de incidente.
 */
public class GatewayGeoException extends RuntimeException {

    public GatewayGeoException(String mensagem) {
        super(mensagem);
    }

    public GatewayGeoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
