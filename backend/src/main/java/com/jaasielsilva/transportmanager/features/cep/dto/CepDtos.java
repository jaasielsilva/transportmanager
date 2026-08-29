package com.jaasielsilva.transportmanager.features.cep.dto;

/**
 * DTOs de consulta de CEP — autofill do form de carga. Entity nunca sai na API;
 * aqui nem ha entity: o CEP vem de um provedor externo (ViaCEP) e nao persiste
 * nada. CEP inexistente devolve data null, nao erro.
 */
public final class CepDtos {

    private CepDtos() {}

    /**
     * Endereco resolvido a partir do CEP. logradouro/bairro podem vir vazios em
     * CEPs de regiao ampla; cidade/uf sao o que o "Calcular rota" usa para
     * montar a origem/destino.
     */
    public record CepDados(
            String cep,
            String logradouro,
            String bairro,
            String cidade,
            String uf) {}
}
