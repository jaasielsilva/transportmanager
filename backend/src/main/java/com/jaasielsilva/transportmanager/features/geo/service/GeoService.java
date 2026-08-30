package com.jaasielsilva.transportmanager.features.geo.service;

import com.jaasielsilva.transportmanager.features.geo.GatewayGeo;
import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.EstimativaRota;
import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.MapaRota;
import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.PontoGeo;
import java.util.List;
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

    /**
     * Monta o mapa da rota (origem, destino e geometria) para o componente
     * Leaflet do front. Sem geometria tracavel, origem/destino ficam null — o
     * front avisa em vez de desenhar um mapa quebrado.
     */
    public MapaRota tracarRota(String origem, String destino) {
        List<double[]> geometria = gateway.tracar(origem, destino);
        if (geometria == null || geometria.isEmpty()) {
            return new MapaRota(null, null, List.of());
        }
        double[] primeiro = geometria.get(0);
        double[] ultimo = geometria.get(geometria.size() - 1);
        return new MapaRota(
                new PontoGeo(primeiro[0], primeiro[1]),
                new PontoGeo(ultimo[0], ultimo[1]),
                geometria);
    }
}
