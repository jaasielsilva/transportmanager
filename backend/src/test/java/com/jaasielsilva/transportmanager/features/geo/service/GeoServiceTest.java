package com.jaasielsilva.transportmanager.features.geo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jaasielsilva.transportmanager.exception.GatewayGeoException;
import com.jaasielsilva.transportmanager.features.geo.GatewayGeo;
import com.jaasielsilva.transportmanager.features.geo.dto.GeoDtos.EstimativaRota;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Teste unitario do GeoService — camada fina que so delega ao gateway. O que
 * importa aqui e que o controller recebe o que o gateway devolveu sem
 * transformacao surpresa, e que falha do upstream propaga como
 * GatewayGeoException (que vira 502 no handler).
 */
@ExtendWith(MockitoExtension.class)
class GeoServiceTest {

    @Mock GatewayGeo gateway;

    GeoService service;

    @BeforeEach
    void setUp() {
        service = new GeoService(gateway);
    }

    @Test
    @DisplayName("devolve a estimativa do gateway tal qual")
    void delegaAoGateway() {
        when(gateway.estimar("Sao Paulo, Brasil", "Campinas, Brasil"))
                .thenReturn(new EstimativaRota(96, 85));

        var estimativa = service.estimarRota("Sao Paulo, Brasil", "Campinas, Brasil");

        assertThat(estimativa.distanciaKm()).isEqualTo(96);
        assertThat(estimativa.tempoEstimadoMinutos()).isEqualTo(85);
        verify(gateway).estimar("Sao Paulo, Brasil", "Campinas, Brasil");
    }

    @Test
    @DisplayName("falha do provedor propaga como GatewayGeoException (502 no handler)")
    void propagaFalhaDoProvedor() {
        when(gateway.estimar("A", "B")).thenThrow(new GatewayGeoException("Google fora do ar."));

        assertThatThrownBy(() -> service.estimarRota("A", "B"))
                .isInstanceOf(GatewayGeoException.class)
                .hasMessageContaining("Google fora do ar.");
    }

    @Test
    @DisplayName("tracarRota monta origem/destino a partir das pontas da geometria")
    void tracarRotaMontaOrigemEDestino() {
        when(gateway.tracar("Sao Paulo, Brasil", "Campinas, Brasil"))
                .thenReturn(List.of(new double[]{-23.55, -46.63}, new double[]{-22.9, -47.06}));

        var mapa = service.tracarRota("Sao Paulo, Brasil", "Campinas, Brasil");

        assertThat(mapa.origem().lat()).isEqualTo(-23.55);
        assertThat(mapa.destino().lat()).isEqualTo(-22.9);
        assertThat(mapa.geometria()).hasSize(2);
    }

    @Test
    @DisplayName("tracarRota sem geometria devolve origem/destino nulos, nao erro")
    void tracarRotaSemGeometriaDevolveNulos() {
        when(gateway.tracar("A", "B")).thenReturn(List.of());

        var mapa = service.tracarRota("A", "B");

        assertThat(mapa.origem()).isNull();
        assertThat(mapa.destino()).isNull();
        assertThat(mapa.geometria()).isEmpty();
    }
}
