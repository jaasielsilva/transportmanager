package com.jaasielsilva.transportmanager.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Molde do kit — copiar para common/PageResponse.java
 *
 * Toda listagem e paginada. Nunca devolva List<T> cru num endpoint de listagem:
 * funciona com 20 registros e derruba a aplicacao com 200 mil.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, D> PageResponse<D> of(Page<E> page, Function<E, D> toDto) {
        return new PageResponse<>(
                page.getContent().stream().map(toDto).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
