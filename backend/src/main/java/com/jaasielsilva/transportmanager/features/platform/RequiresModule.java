package com.jaasielsilva.transportmanager.features.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Molde do kit — features/platform/RequiresModule.java
 *
 * Marca um endpoint (ou um controller inteiro) como pertencente a um modulo
 * vendavel. Quem nao tem o modulo no plano recebe 403 com mensagem clara.
 *
 * ESTE E O BLOQUEIO DE VERDADE. O menu do front esconder o item nao bloqueia
 * nada: basta o cliente digitar a URL, ou chamar a API direto. Modulo pago sem
 * checagem no backend nao esta vendido, esta doado.
 *
 * Uso — prefira na CLASSE quando o controller inteiro pertence ao modulo:
 *
 *   @RestController
 *   @RequestMapping("/api/v1/agenda")
 *   @RequiresModule(Modulo.OPERACAO)
 *   public class AgendaController { ... }
 *
 * No metodo, quando so uma acao e do modulo (ex.: exportar relatorio):
 *
 *   @GetMapping("/exportar")
 *   @RequiresModule(Modulo.RELATORIOS)
 *   public ... exportar() { ... }
 *
 * O que NUNCA leva esta anotacao: auth, billing, /me e configuracoes basicas.
 * O cliente precisa sempre conseguir entrar e pagar, mesmo sem modulo nenhum.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresModule {

    Modulo value();
}
