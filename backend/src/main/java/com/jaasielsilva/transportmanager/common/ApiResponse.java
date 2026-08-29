package com.jaasielsilva.transportmanager.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Molde do kit — copiar para common/ApiResponse.java (+ FieldError abaixo).
 *
 * Envelope unico de TODA resposta da API. O controller nunca devolve a entidade
 * nem um Map solto: o front tem um unico formato para tratar e o interceptor de
 * erro do Angular consegue ser generico.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        List<FieldError> errors
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static ApiResponse<Void> error(String message, List<FieldError> errors) {
        return new ApiResponse<>(false, null, message, errors);
    }

    /** Erro por campo — alimenta a validacao inline do Reactive Form. */
    public record FieldError(String field, String message) {}
}
