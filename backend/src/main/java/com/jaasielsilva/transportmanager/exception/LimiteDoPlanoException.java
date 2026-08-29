package com.jaasielsilva.transportmanager.exception;

/**
 * 409 com caminho de upgrade. Momento comercial, nao mensagem de erro:
 * a mensagem diz qual limite estourou e o que fazer para sair dele.
 */
public class LimiteDoPlanoException extends RegraDeNegocioException {
    public LimiteDoPlanoException(String chave, long limite) {
        super("Seu plano permite ate " + limite + " (" + chave
                + "). Faca upgrade para continuar.");
    }
}
