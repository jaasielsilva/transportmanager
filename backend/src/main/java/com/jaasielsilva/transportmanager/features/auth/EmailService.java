package com.jaasielsilva.transportmanager.features.auth;

import com.jaasielsilva.transportmanager.features.platform.Empresa;

/**
 * Molde do kit — features/auth/EmailService.java
 *
 * Interface para o envio nao depender do provedor (SMTP, SendGrid, SES).
 * Todo envio e assincrono: e-mail lento nao pode segurar a resposta HTTP do
 * usuario, e provedor fora do ar nao pode derrubar um signup.
 *
 * Em desenvolvimento o mailpit do docker-compose captura tudo
 * (http://localhost:8025) — nunca configure SMTP real no profile dev.
 */
public interface EmailService {

    void enviarBoasVindas(Usuario usuario, Empresa empresa);

    /** Link de uso unico, expira em 7 dias. O convidado define a propria senha. */
    void enviarConvite(Usuario usuario, String tokenBruto);

    /** Link de uso unico, expira em 30 min. */
    void enviarResetDeSenha(Usuario usuario, String tokenBruto);

    /** Etapas 1 a 3 da regua de dunning. */
    void enviarAvisoDeCobranca(Empresa empresa, int etapa, String mensagem);

    void enviarSuspensao(Empresa empresa);

    void enviarCancelamento(Empresa empresa);
}
