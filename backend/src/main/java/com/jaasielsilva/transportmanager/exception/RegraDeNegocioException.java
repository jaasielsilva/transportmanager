package com.jaasielsilva.transportmanager.exception;

/** 409. Conflito de regra de negocio: e-mail duplicado, status invalido, etc. */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
