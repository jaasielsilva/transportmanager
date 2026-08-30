package com.jaasielsilva.transportmanager.features.motorista.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * DTOs de Motorista — molde do kit (CargaDtos). Entity nunca sai na API.
 */
public final class MotoristaDtos {

    private MotoristaDtos() {}

    /** Linha da tabela - resumo para listagem. */
    public record Resumo(
            Long id,
            String nome,
            String telefone,
            boolean ativo) {}

    /** Tela de detalhe/edicao. */
    public record Detalhe(
            Long id,
            String nome,
            String cnh,
            String telefone,
            String email,
            Long usuarioId,
            boolean ativo,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm) {}

    /** Mesmo record para criar e editar. */
    public record SalvarRequest(
            @NotBlank(message = "Informe o nome do motorista.")
            @Size(max = 150)
            String nome,

            @Size(max = 20)
            String cnh,

            @Size(max = 20)
            String telefone,

            @Email(message = "E-mail invalido.")
            @Size(max = 150)
            String email,

            Long usuarioId,

            Boolean ativo) {}
}
