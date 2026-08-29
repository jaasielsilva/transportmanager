package com.jaasielsilva.transportmanager.features.cep.service;

import com.jaasielsilva.transportmanager.features.cep.GatewayCep;
import com.jaasielsilva.transportmanager.features.cep.dto.CepDtos.CepDados;
import org.springframework.stereotype.Service;

/**
 * Camada fina entre controller e gateway (mesmo papel do GeoService). Deixa o
 * controller sem conhecimento do provedor e da um ponto unico para regra futura
 * (ex.: cache do CEP).
 */
@Service
public class CepService {

    private final GatewayCep gateway;

    public CepService(GatewayCep gateway) {
        this.gateway = gateway;
    }

    public CepDados buscar(String cep) {
        return gateway.buscar(cep);
    }
}
