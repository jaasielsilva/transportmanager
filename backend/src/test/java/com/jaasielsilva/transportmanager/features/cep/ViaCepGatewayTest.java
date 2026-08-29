package com.jaasielsilva.transportmanager.features.cep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jaasielsilva.transportmanager.exception.GatewayCepException;
import com.jaasielsilva.transportmanager.features.cep.dto.CepDtos.CepDados;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Teste HTTP do gateway ViaCEP: valida o contrato — GET em /ws/{cep}/json/ e o
 * parse do JSON. Nada cai na rede; o viacep e gratuito, sem chave.
 */
class ViaCepGatewayTest {

    private static final String CEP = "01001000";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private ViaCepGateway gateway;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new ViaCepGateway(builder);
    }

    @Test
    @DisplayName("CEP valido vira CepDados preenchido")
    void cepValidoDevolveEndereco() {
        server.expect(requisicaoDoCep(CEP)).andRespond(withSuccess("""
                {
                  "cep": "01001-000",
                  "logradouro": "Praca da Se",
                  "complemento": "lado impar",
                  "bairro": "Se",
                  "localidade": "Sao Paulo",
                  "uf": "SP"
                }
                """, MediaType.APPLICATION_JSON));

        CepDados dados = gateway.buscar(CEP);

        assertThat(dados.cep()).isEqualTo(CEP);
        assertThat(dados.logradouro()).isEqualTo("Praca da Se");
        assertThat(dados.bairro()).isEqualTo("Se");
        assertThat(dados.cidade()).isEqualTo("Sao Paulo");
        assertThat(dados.uf()).isEqualTo("SP");
        server.verify();
    }

    @Test
    @DisplayName("CEP inexistente (erro:true) devolve null, nao erro")
    void cepInexistenteDevolveNulo() {
        server.expect(requisicaoDoCep(CEP))
                .andRespond(withSuccess("{\"erro\": true}", MediaType.APPLICATION_JSON));

        CepDados dados = gateway.buscar(CEP);

        assertThat(dados).isNull();
        server.verify();
    }

    @Test
    @DisplayName("falha HTTP (5xx) do ViaCEP vira GatewayCepException (502)")
    void falhaHttpLancaExcecao() {
        server.expect(requisicaoDoCep(CEP)).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.buscar(CEP))
                .isInstanceOf(GatewayCepException.class);
        server.verify();
    }

    @Test
    @DisplayName("CEP malformado falha cedo, sem chamar o ViaCEP")
    void cepMalformadoLancaExcecaoSemChamar() {
        assertThatThrownBy(() -> gateway.buscar("123"))
                .isInstanceOf(GatewayCepException.class)
                .hasMessageContaining("8 digitos");
        server.verify(); // nenhuma requisicao saiu
    }

    /** GET /ws/{cep}/json/ com o CEP no path. */
    private static org.springframework.test.web.client.RequestMatcher requisicaoDoCep(String cep) {
        return request -> {
            assertThat(request.getMethod()).isEqualTo(GET);
            assertThat(request.getURI().getPath()).isEqualTo("/ws/" + cep + "/json/");
        };
    }
}
