package com.jaasielsilva.transportmanager.features.geo;

import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.EstimativaRota;

/**
 * Fronteira com o provedor de geolocalizacao — mesmo padrao do GatewayBilling:
 * TUDO que e especifico do provedor (URL, JSON, status de erro) fica atras
 * desta interface. Trocar de provedor vira uma implementacao nova.
 */
public interface GatewayGeo {

    /** Estimativa de rota entre dois enderecos. Nunca lança checked exception. */
    EstimativaRota estimar(String origem, String destino);
}
