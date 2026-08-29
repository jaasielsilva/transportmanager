package com.jaasielsilva.transportmanager.config;

import com.jaasielsilva.transportmanager.features.billing.AssinaturaAccessInterceptor;
import com.jaasielsilva.transportmanager.features.platform.ModuloInterceptor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Molde do kit — config/WebMvcConfig.java
 *
 * Registra o interceptor que aplica a regua de dunning. Sem este registro a
 * regua calcula tudo certinho e nao bloqueia nada — o enforcement mora aqui.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AssinaturaAccessInterceptor assinaturaInterceptor;
    private final ModuloInterceptor moduloInterceptor;

    public WebMvcConfig(AssinaturaAccessInterceptor assinaturaInterceptor,
                        ModuloInterceptor moduloInterceptor) {
        this.assinaturaInterceptor = assinaturaInterceptor;
        this.moduloInterceptor = moduloInterceptor;
    }

    /**
     * A ORDEM importa e nao e arbitraria.
     *
     * Assinatura primeiro: quem esta com pagamento em atraso recebe 402 (com
     * caminho para regularizar) em vez de 403 "modulo nao contratado", que
     * mandaria o cliente para a tela de upgrade errada.
     */
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(assinaturaInterceptor).addPathPatterns("/api/v1/**").order(1);
        registry.addInterceptor(moduloInterceptor).addPathPatterns("/api/v1/**").order(2);
    }

    @Bean
    public OpenAPI openApi() {
        final String esquema = "bearer-jwt";
        return new OpenAPI()
                .info(new Info().title("TransportManager API").version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(esquema))
                .components(new Components().addSecuritySchemes(esquema,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
