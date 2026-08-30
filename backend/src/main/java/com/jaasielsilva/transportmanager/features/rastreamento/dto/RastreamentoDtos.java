package com.jaasielsilva.transportmanager.features.rastreamento.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTOs de rastreamento GPS. Entity nunca sai na API.
 */
public final class RastreamentoDtos {

    private RastreamentoDtos() {}

    /** Corpo do POST feito pelo navegador do motorista a cada ~12s. */
    public record RegistrarPosicaoRequest(
            @NotNull(message = "Informe a latitude.")
            BigDecimal latitude,

            @NotNull(message = "Informe a longitude.")
            BigDecimal longitude) {}

    /** Ultima posicao conhecida — o que o mapa de quem acompanha consulta a cada ~8s. */
    public record PosicaoAtual(
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime registradoEm) {}
}
