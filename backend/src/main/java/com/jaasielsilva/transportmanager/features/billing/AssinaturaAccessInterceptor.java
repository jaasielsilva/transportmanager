package com.jaasielsilva.transportmanager.features.billing;

import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.features.billing.ReguaDeDunning.NivelAcesso;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Molde do kit — copiar para features/billing/AssinaturaAccessInterceptor.java
 *
 * Aplica o nivel de acesso decidido pela regua de dunning. E o unico ponto de
 * bloqueio — esconder o botao no front nao bloqueia nada.
 *
 * A LISTA DE ISENTOS E A PARTE MAIS IMPORTANTE DESTA CLASSE. Cliente que nao
 * consegue entrar nao consegue pagar; cliente que nao alcanca a tela de
 * pagamento nunca sai do bloqueio. Bloquear billing e auth transforma um atraso
 * de cobranca em churn definitivo.
 *
 * Registrar em WebMvcConfigurer:
 *   registry.addInterceptor(assinaturaAccessInterceptor).addPathPatterns("/api/v1/**");
 */
@Component
public class AssinaturaAccessInterceptor implements HandlerInterceptor {

    /** Sempre acessiveis, em qualquer nivel — inclusive suspenso. */
    private static final List<String> ISENTOS = List.of(
            "/api/v1/auth",              // login, refresh, logout, esqueci-senha
            "/api/v1/public",            // signup, webhooks
            "/api/v1/billing",           // plano, faturas, atualizar cartao
            "/api/v1/me",                // perfil e contexto do usuario logado
            "/api/v1/platform",          // PLATFORM_ADMIN nao e afetado pelo tenant
            "/actuator"
    );

    private final AssinaturaService assinaturaService;

    public AssinaturaAccessInterceptor(AssinaturaService assinaturaService) {
        this.assinaturaService = assinaturaService;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
            throws Exception {

        String path = req.getRequestURI();
        if (ISENTOS.stream().anyMatch(path::startsWith)) {
            return true;
        }

        Long empresaId = TenantContext.get();
        if (empresaId == null) {
            return true;                  // sem tenant: quem barra e o Spring Security
        }

        NivelAcesso nivel = TenantContext.semFiltroDeTenant(
                () -> assinaturaService.nivelDeAcessoDe(empresaId));

        switch (nivel) {
            case NORMAL -> {
                return true;
            }
            case SOMENTE_LEITURA -> {
                // Leitura continua liberada: o cliente ve os dados dele e nao
                // sente que perdeu o trabalho — e isso que o traz de volta.
                if (isLeitura(req.getMethod())) {
                    return true;
                }
                bloquear(res, "Sua conta esta em modo somente leitura ate a regularizacao do pagamento.");
                return false;
            }
            case SUSPENSO -> {
                bloquear(res, "Sua conta esta suspensa. Regularize o pagamento para reativar — nenhum dado foi excluido.");
                return false;
            }
        }
        return true;
    }

    private boolean isLeitura(String metodo) {
        return "GET".equals(metodo) || "HEAD".equals(metodo) || "OPTIONS".equals(metodo);
    }

    /**
     * 402 Payment Required — distingue "problema de pagamento" de "sem permissao"
     * (403). O interceptor de erro do Angular usa isso para levar direto a tela
     * de regularizacao em vez de mostrar um toast generico.
     */
    private void bloquear(HttpServletResponse res, String mensagem) throws Exception {
        res.setStatus(402);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("""
                {"success":false,"data":null,"message":"%s"}""".formatted(mensagem));
    }
}
