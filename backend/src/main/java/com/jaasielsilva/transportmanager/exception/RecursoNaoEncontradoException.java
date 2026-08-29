package com.jaasielsilva.transportmanager.exception;

/** 404. Tambem usada para recurso de OUTRO tenant — nunca vazar 403 entre empresas. */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
