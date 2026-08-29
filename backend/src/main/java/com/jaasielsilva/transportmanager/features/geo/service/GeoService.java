package com.jaasielsilva.transportmanager.features.geo.service;

import com.jaasielsilva.transportmanager.features.geo.GatewayGeo;
import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.EstimativaRota;
import org.springframework.stereotype.Service;

/**
 * Servico geo — camada fina sobre o gateway. O CargaController chama este
 * servico; so o gateway conhece o provedor.
 */
@Service
public class GeoService {

    private final GatewayGeo gateway;

    public GeoService(GatewayGeo gateway) {
        this.gateway = gateway;
    }

    public EstimativaRota estimarRota(String origem, String destino) {
        return gateway.estimar(origem, destino);
    }
}
