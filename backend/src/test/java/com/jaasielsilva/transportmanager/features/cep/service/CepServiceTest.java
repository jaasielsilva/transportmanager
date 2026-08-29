package com.jaasielsilva.transportmanager.features.cep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jaasielsilva.transportmanager.exception.GatewayCepException;
import com.jaasielsilva.transportmanager.features.cep.GatewayCep;
import com.jaasielsilva.transportmanager.features.cep.dto.CepDtos.CepDados;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Teste unitario do CepService — camada fina que so delega ao gateway. O que
 * importa: o controller recebe o que o gateway devolveu (inclusive null para
 * CEP inexistente) e falha do upstream propaga como GatewayCepException (502).
 */
@ExtendWith(MockitoExtension.class)
class CepServiceTest {

    @Mock GatewayCep gateway;

    CepService service;

    @BeforeEach
    void setUp() {
        service = new CepService(gateway);
    }

    @Test
    @DisplayName("devolve o endereco do gateway tal qual")
    void delegaAoGateway() {
        when(gateway.buscar("01001000"))
                .thenReturn(new CepDados("01001000", "Praca da Se", "Se", "Sao Paulo", "SP"));

        var dados = service.buscar("01001000");

        assertThat(dados.cidade()).isEqualTo("Sao Paulo");
        assertThat(dados.uf()).isEqualTo("SP");
        verify(gateway).buscar("01001000");
    }

    @Test
    @DisplayName("CEP inexistente (null do gateway) repassa null — nao e erro")
    void repassaNullDeCepInexistente() {
        when(gateway.buscar("99999999")).thenReturn(null);

        assertThat(service.buscar("99999999")).isNull();
        verify(gateway).buscar("99999999");
    }

    @Test
    @DisplayName("falha do provedor propaga como GatewayCepException (502 no handler)")
    void propagaFalhaDoProvedor() {
        when(gateway.buscar("01001000")).thenThrow(new GatewayCepException("ViaCEP fora do ar."));

        assertThatThrownBy(() -> service.buscar("01001000"))
                .isInstanceOf(GatewayCepException.class)
                .hasMessageContaining("ViaCEP fora do ar.");
    }
}
