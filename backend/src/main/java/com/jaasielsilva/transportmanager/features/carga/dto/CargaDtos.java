package com.jaasielsilva.transportmanager.features.carga.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTOs para Cargas de Transporte - Evoluídos do CRUD genérico do kit
 * 
 * Entity NUNCA sai na API. O motivo prático: no dia em que a entity ganhar uma
 * coluna interna (custo, score, flag de fraude), ela vazaria sozinha para o
 * front de todos os clientes, sem ninguém decidir isso.
 *
 * DTO de listagem separado do de detalhe de propósito: a lista carrega o que a
 * tabela mostra, e nada mais.
 *
 * Repare que não existe empresaId em nenhum request. O tenant vem do token —
 * aceita-lo do corpo permitiria gravar na empresa dos outros.
 */
public final class CargaDtos {

    private CargaDtos() {}

    /** Linha da tabela - resumo para listagem */
    public record Resumo(
            Long id,
            String nome,
            String status,
            String origemCidade,
            String origemUf,
            String destinoCidade,
            String destinoUf,
            BigDecimal valorFrete,
            boolean ativo) {}

    /** Tela de detalhe/edição - completa com campos de transporte */
    public record Detalhe(
            Long id,
            String nome,
            String email,
            String telefone,
            String documento,
            String observacao,
            boolean ativo,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm,
            // Campos específicos de transporte
            String origemEndereco,
            String origemCidade,
            String origemUf,
            String destinoEndereco,
            String destinoCidade,
            String destinoUf,
            BigDecimal peso,
            BigDecimal valorFrete,
            String status,
            Long motoristaId,
            Long clienteId,
            LocalDateTime dataColeta,
            LocalDateTime dataEntregaPrevista,
            LocalDateTime dataEntregaReal,
            Integer distanciaKm,
            Integer tempoEstimadoMinutos) {}

    /**
     * Request para criar/editar carga - campos específicos de transporte
     * Mesmo record para criar e editar: os campos são os mesmos e dois DTOs
     * idênticos só criam a chance de um ganhar validação que o outro não tem.
     */
    public record SalvarRequest(
            // Campos originais do CRUD genérico
            @NotBlank(message = "Informe o nome da carga.")
            @Size(max = 150)
            String nome,

            @Email(message = "E-mail inválido.")
            @Size(max = 150)
            String email,

            @Size(max = 20)
            String telefone,

            @Size(max = 20)
            String documento,

            @Size(max = 500)
            String observacao,

            Boolean ativo,

            // Campos específicos de transporte
            @Size(max = 255)
            String origemEndereco,

            @NotBlank(message = "Informe a cidade de origem.")
            @Size(max = 100)
            String origemCidade,

            @Size(max = 2)
            String origemUf,

            @Size(max = 255)
            String destinoEndereco,

            @NotBlank(message = "Informe a cidade de destino.")
            @Size(max = 100)
            String destinoCidade,

            @Size(max = 2)
            String destinoUf,

            @Positive(message = "O peso deve ser positivo.")
            BigDecimal peso,

            @Positive(message = "O valor do frete deve ser positivo.")
            BigDecimal valorFrete,

            String status,

            Long motoristaId,

            Long clienteId,

            LocalDateTime dataColeta,

            LocalDateTime dataEntregaPrevista,

            LocalDateTime dataEntregaReal,

            Integer distanciaKm,

            Integer tempoEstimadoMinutos) {}

    /** DTO para atualização de status da carga */
    public record AtualizarStatusRequest(
            @NotBlank(message = "Informe o novo status.")
            String status) {}
}
