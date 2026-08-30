package com.jaasielsilva.transportmanager.exception;

/**
 * 403. Autenticado e com a role certa, mas sem posse do recurso — regra que
 * depende do DADO (ex.: motorista tentando reportar posicao de uma carga que
 * nao e dele), e por isso @PreAuthorize (que so ve role) nao cobre. Nao usar
 * para "sem role": isso e AccessDeniedException do Spring Security.
 */
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
