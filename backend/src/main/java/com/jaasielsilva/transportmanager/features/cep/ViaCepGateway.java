package com.jaasielsilva.transportmanager.features.cep;

import com.jaasielsilva.transportmanager.exception.GatewayCepException;
import com.jaasielsilva.transportmanager.features.cep.dto.CepDtos.CepDados;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Gateway de CEP via ViaCEP (https://viacep.com.br) — gratuito, sem chave, sem
 * cartao (escolha coerente com o OpenRouteService). Unica classe que conhece o
 * JSON do ViaCEP: GET /ws/{cep}/json/. CEP inexistente responde 200 com
 * {"erro": true} -> devolvemos null (o form avisa "CEP nao encontrado").
 */
@Component
public class ViaCepGateway implements GatewayCep {

    private static final Logger log = LoggerFactory.getLogger(ViaCepGateway.class);

    private static final String BASE_URL = "https://viacep.com.br";

    private final RestClient restClient;

    public ViaCepGateway(RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public CepDados buscar(String cep) {
        String digitos = cep == null ? "" : cep.replaceAll("\\D", "");
        if (digitos.length() != 8) {
            // Defesa extra: o controller ja valida \d{8}, mas um gateway nao
            // deve confiar em quem o chama.
            throw new GatewayCepException("CEP invalido: informe 8 digitos.");
        }

        RespostaViaCep resposta;
        try {
            resposta = restClient.get()
                    .uri("/ws/{cep}/json/", digitos)
                    .retrieve()
                    .body(RespostaViaCep.class);
        } catch (Exception e) {
            // HTTP != 2xx, timeout, conexao recusada... tudo vira 502.
            log.warn("Falha ao consultar ViaCEP: cep={}", digitos, e);
            throw new GatewayCepException("Nao foi possivel consultar o CEP.", e);
        }

        if (resposta == null || Boolean.TRUE.equals(resposta.erro())) {
            // CEP valido de formato mas sem correspondencia no correio.
            log.info("ViaCEP sem resultado: cep={}", digitos);
            return null;
        }

        return new CepDados(digitos, resposta.logradouro(), resposta.bairro(),
                resposta.localidade(), resposta.uf());
    }

    /** Estrutura do JSON do ViaCEP — so o que a gente usa. */
    private record RespostaViaCep(
            String cep,
            String logradouro,
            String bairro,
            String localidade,
            String uf,
            Boolean erro) {}
}
