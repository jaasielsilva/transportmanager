package com.jaasielsilva.transportmanager.features.platform;

import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.ModuloNaoContratadoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Molde do kit — features/platform/ModuloInterceptor.java
 *
 * Faz valer o @RequiresModule. Sem esta classe a anotacao seria decoracao:
 * o endpoint continuaria aberto para qualquer plano.
 *
 * Lanca ModuloNaoContratadoException -> 403 pelo GlobalExceptionHandler, com
 * mensagem que diz o que aconteceu ("nao esta incluido no seu plano") em vez de
 * um "acesso negado" generico que faz o cliente abrir chamado no suporte.
 *
 * HandlerInterceptor em vez de aspecto AOP de proposito: o AssinaturaAccessInterceptor
 * ja funciona assim, nao exige dependencia nova e roda antes de o controller
 * ser chamado. Um jeito so de bloquear e mais facil de auditar que dois.
 */
@Component
public class ModuloInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ModuloInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return true;                  // recurso estatico, erro, etc.
        }

        RequiresModule exigencia = metodo.getMethodAnnotation(RequiresModule.class);
        if (exigencia == null) {
            // Fallback para a anotacao na classe — o caso mais comum, quando o
            // controller inteiro pertence ao modulo.
            exigencia = metodo.getBeanType().getAnnotation(RequiresModule.class);
        }
        if (exigencia == null) {
            return true;                  // endpoint sem modulo: liberado
        }

        // PLATFORM_ADMIN opera a plataforma e precisa enxergar tudo para dar
        // suporte. Nao e cliente, nao tem plano.
        if (isPlatformAdmin()) {
            return true;
        }

        String modulo = exigencia.value().name();
        if (TenantContext.temModulo(modulo)) {
            return true;
        }

        // WARN e nao ERROR: nao e falha do sistema, e cliente batendo em porta
        // que nao comprou. Em volume, vira sinal comercial — endpoint muito
        // procurado por quem nao tem o modulo e candidato a upsell.
        log.warn("Modulo {} negado para empresa {} em {}",
                modulo, TenantContext.get(), req.getRequestURI());
        throw new ModuloNaoContratadoException(modulo);
    }

    private boolean isPlatformAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
    }
}
