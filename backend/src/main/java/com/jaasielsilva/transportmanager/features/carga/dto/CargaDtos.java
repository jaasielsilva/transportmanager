package com.jaasielsilva.transportmanager.features.carga.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Molde do kit — features/carga/dto/CargaDtos.java
 *
 * Entity NUNCA sai na API. O motivo pratico: no dia em que a entity ganhar uma
 * coluna interna (custo, score, flag de fraude), ela vazaria sozinha para o
 * front de todos os clientes, sem ninguem decidir isso.
 *
 * DTO de listagem separado do de detalhe de proposito: a lista carrega o que a
 * tabela mostra, e nada mais.
 *
 * Repare que nao existe empresaId em nenhum request. O tenant vem do token —
 * aceita-lo do corpo permitiria gravar na empresa dos outros.
 */
public final class CargaDtos {

    private CargaDtos() {}

    /** Linha da tabela. */
    public record Resumo(
            Long id,
            String nome,
            String email,
            String telefone,
            boolean ativo) {}

    /** Tela de detalhe/edicao. */
    public record Detalhe(
            Long id,
            String nome,
            String email,
            String telefone,
            String documento,
            String observacao,
            boolean ativo,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm) {}

    /**
     * Mesmo record para criar e editar: os campos sao os mesmos e dois DTOs
     * identicos so criam a chance de um ganhar validacao que o outro nao tem.
     */
    public record SalvarRequest(
            @NotBlank(message = "Informe o nome.")
            @Size(max = 150)
            String nome,

            @Email(message = "E-mail invalido.")
            @Size(max = 150)
            String email,

            @Size(max = 20)
            String telefone,

            @Size(max = 20)
            String documento,

            @Size(max = 500)
            String observacao,

            Boolean ativo) {}
}
