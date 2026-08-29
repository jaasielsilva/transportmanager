package com.jaasielsilva.transportmanager.features.platform;

/**
 * Molde do kit — features/platform/Modulo.java
 *
 * Codigo estavel de cada modulo vendavel. E a chave que liga plano, menu e
 * @RequiresModule — por isso o nome NUNCA muda depois de entrar em producao
 * (esta gravado em plano_modulos no banco de todos os clientes).
 *
 * Auth e configuracoes basicas nao entram aqui: o cliente sempre precisa
 * conseguir entrar e pagar, mesmo sem modulo nenhum.
 *
 * Troque pelos modulos do SEU projeto e preencha a tabela "Modulos x planos"
 * na ARQUITETURA antes de codar o primeiro deles.
 */
public enum Modulo {
    CADASTROS,
    OPERACAO,
    RELATORIOS
}
