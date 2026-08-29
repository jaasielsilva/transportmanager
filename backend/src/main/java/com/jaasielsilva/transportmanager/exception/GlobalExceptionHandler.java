package com.jaasielsilva.transportmanager.exception;

import com.jaasielsilva.transportmanager.common.ApiResponse;
import com.jaasielsilva.transportmanager.common.ApiResponse.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Molde do kit — copiar para exception/GlobalExceptionHandler.java
 *
 * Unico lugar que traduz excecao em resposta HTTP. Controller NUNCA faz
 * try/catch devolvendo erro manual: perde padronizacao, perde log e perde
 * traceId.
 *
 * Exige tambem (arquivos irmaos, uma linha cada):
 *   RecursoNaoEncontradoException  extends RuntimeException
 *   RegraDeNegocioException        extends RuntimeException  -> 409
 *   LimiteDoPlanoException         extends RegraDeNegocioException
 *   ModuloNaoContratadoException   extends RuntimeException  -> 403
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 404 — tambem usado para recurso de OUTRO tenant: nunca vazar 403 entre empresas. */
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ApiResponse<Void>> naoEncontrado(RecursoNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    /** 409 — conflito de regra de negocio (e-mail duplicado, status invalido...). */
    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ApiResponse<Void>> regraDeNegocio(RegraDeNegocioException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 409 com caminho de upgrade. E um momento comercial, nao so um erro:
     * a mensagem tem que dizer qual o limite e o que fazer para sair dele.
     */
    @ExceptionHandler(LimiteDoPlanoException.class)
    public ResponseEntity<ApiResponse<Void>> limiteDoPlano(LimiteDoPlanoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
    }

    /** 403 — modulo fora do plano contratado. */
    @ExceptionHandler(ModuloNaoContratadoException.class)
    public ResponseEntity<ApiResponse<Void>> moduloNaoContratado(ModuloNaoContratadoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
    }

    /** 400 — Bean Validation nos DTOs de request. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validacao(MethodArgumentNotValidException e) {
        List<FieldError> erros = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Verifique os campos informados.", erros));
    }

    /**
     * 502 — o upstream (Google Maps) falhou ou nao esta configurado. Nao e um
     * bug nosso: a mensagem e amigavel e o detalhe real fica no log (o gateway
     * ja logou a causa antes de lancar).
     */
    @ExceptionHandler(GatewayGeoException.class)
    public ResponseEntity<ApiResponse<Void>> gatewayGeo(GatewayGeoException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("Nao foi possivel calcular a rota agora. Tente novamente."));
    }

    /** 403 — autenticado, mas sem a role exigida pelo @PreAuthorize. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> acessoNegado(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Voce nao tem permissao para esta acao."));
    }

    /**
     * 500 — o cliente recebe uma mensagem generica com o traceId; o detalhe
     * completo fica no log. Nunca devolver stack trace: e vazamento de
     * informacao e assusta o usuario.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> erroInesperado(Exception e, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] Erro inesperado em {} {}", traceId, req.getMethod(), req.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "Erro inesperado. Informe o codigo " + traceId + " ao suporte."));
    }
}
