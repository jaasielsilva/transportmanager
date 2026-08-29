package com.jaasielsilva.transportmanager.config.security;

import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Molde do kit — config/security/JwtAuthFilter.java
 *
 * Valida o token e preenche DUAS coisas: o SecurityContext (quem e) e o
 * TenantContext (de qual empresa). O tenant vem SEMPRE do token — nunca de
 * body, query ou header, que o cliente controla.
 *
 * O bloco finally com TenantContext.clear() nao e detalhe: o Tomcat reaproveita
 * threads entre requisicoes. Sem a limpeza, a proxima requisicao que cair
 * naquela thread herda o tenant da anterior e le dados de outra empresa. Falha
 * silenciosa, intermitente e gravissima.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = extrairToken(request);
            if (token != null) {
                Claims claims = jwtService.validar(token);
                if (claims != null) {
                    autenticar(request, claims);
                }
                // Token invalido nao lanca erro aqui: segue sem autenticacao e
                // quem devolve 401 e o entry point do Spring Security.
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @SuppressWarnings("unchecked")
    private void autenticar(HttpServletRequest request, Claims claims) {
        Long usuarioId = Long.valueOf(claims.getSubject());

        Number empresaId = claims.get("empresaId", Number.class);
        if (empresaId != null) {
            TenantContext.set(empresaId.longValue());
        }

        // Modulos do plano vem assinados no token — o @RequiresModule consulta
        // daqui, sem ir ao banco em toda requisicao.
        Collection<String> modulos = claims.get("modules", Collection.class);
        TenantContext.setModulos(modulos);

        Collection<String> roles = claims.get("roles", Collection.class);
        List<SimpleGrantedAuthority> authorities = (roles == null ? List.<String>of() : roles)
                .stream()
                // Spring Security espera o prefixo ROLE_ para hasRole(...)
                .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
                .toList();

        var auth = new UsernamePasswordAuthenticationToken(usuarioId, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String extrairToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
