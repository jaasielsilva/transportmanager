package com.jaasielsilva.transportmanager.exception;

/**
 * Falha ao falar com o provedor de CEP (ViaCEP fora do ar, timeout, CEP
 * malformado).
 *
 * Vira 502 BAD_GATEWAY no GlobalExceptionHandler: o upstream que falhou foi o
 * ViaCEP, nao a nossa aplicacao — mesmo raciocinio do GatewayGeoException com o
 * provedor de rota.
 */
public class GatewayCepException extends RuntimeException {

    public GatewayCepException(String mensagem) {
        super(mensagem);
    }

    public GatewayCepException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
