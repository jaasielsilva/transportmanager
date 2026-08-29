package com.jaasielsilva.transportmanager.exception;

/** 403. Modulo fora do plano contratado. */
public class ModuloNaoContratadoException extends RuntimeException {
    public ModuloNaoContratadoException(String modulo) {
        super("O modulo " + modulo + " nao esta incluido no seu plano.");
    }
}
