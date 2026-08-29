package com.jaasielsilva.transportmanager.config.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Molde do kit — config/security/SecurityConfig.java
 *
 * @EnableMethodSecurity liga o @PreAuthorize dos controllers. Ele e a
 * autorizacao de verdade: o front esconder o botao nao protege nada.
 *
 * Sessao STATELESS — quem carrega a identidade e o token, nao um JSESSIONID.
 * Por isso o CSRF fica desligado: sem cookie de sessao usado para autenticar,
 * nao existe o ataque que ele previne. (O cookie do refresh e SameSite=Strict
 * e so a rota /auth/refresh o consome.)
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final String origensPermitidas;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          @Value("${app.cors.allowed-origins}") String origensPermitidas) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.origensPermitidas = origensPermitidas;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Publicos: login, signup, webhook do gateway, recuperacao de senha
                        .requestMatchers("/api/v1/auth/**", "/api/v1/public/**").permitAll()
                        // Health fica aberto para o healthcheck do container;
                        // o Nginx da VPS ja nega /actuator/** vindo de fora.
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                // 401 seco em vez de redirecionar para uma pagina de login que nao existe numa API
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        // Origens explicitas — nunca "*" com credenciais
        config.setAllowedOrigins(List.of(origensPermitidas.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);       // o cookie httpOnly do refresh precisa disto
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
