package com.jaasielsilva.transportmanager.features.cep;

import com.jaasielsilva.transportmanager.features.cep.dto.CepDtos.CepDados;

/**
 * Contrato de consulta de CEP. O provedor real (ViaCEP) fica atras desta
 * interface — trocar de provedor vira implementacao nova, sem tocar no
 * controller (mesmo padrao do {@code GatewayGeo}).
 */
public interface GatewayCep {

    /**
     * CEP inexistente no provedor -> null (nao e erro). Falha HTTP/timeout do
     * provedor -> {@link com.jaasielsilva.transportmanager.exception.GatewayCepException}
     * (vira 502 no GlobalExceptionHandler).
     */
    CepDados buscar(String cep);
}
