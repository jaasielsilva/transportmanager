package com.jaasielsilva.transportmanager.features.auth;

import com.jaasielsilva.transportmanager.common.ApiResponse;
import com.jaasielsilva.transportmanager.features.auth.AuthDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Molde do kit — features/auth/AuthController.java
 *
 * Rotas publicas de autenticacao. O access token vai no corpo (o Angular guarda
 * so em memoria); o refresh token vai num cookie httpOnly que o JavaScript nao
 * consegue ler — e isso que impede um XSS de virar conta roubada.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String COOKIE_REFRESH = "refresh_token";

    private final AuthService authService;
    private final OnboardingService onboardingService;

    public AuthController(AuthService authService, OnboardingService onboardingService) {
        this.authService = authService;
        this.onboardingService = onboardingService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest req, HttpServletRequest http) {

        var resultado = authService.login(req, ipDe(http));
        return comCookie(resultado);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = COOKIE_REFRESH, required = false) String refreshToken,
            HttpServletRequest http) {

        var resultado = authService.refresh(refreshToken, ipDe(http));
        return comCookie(resultado);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = COOKIE_REFRESH, required = false) String refreshToken) {

        authService.logout(refreshToken);
        // maxAge=0 apaga o cookie no navegador
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh("", 0).toString())
                .body(ApiResponse.ok(null, "Sessao encerrada."));
    }

    /** Signup self-service: cria empresa + primeiro TENANT_ADMIN numa transacao so. */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest req) {
        onboardingService.criarTenant(req);
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(null, "Conta criada. Faca login para comecar."));
    }

    @PostMapping("/esqueci-senha")
    public ApiResponse<Void> esqueciSenha(@Valid @RequestBody EsqueciSenhaRequest req) {
        authService.esqueciSenha(req);
        // Resposta identica exista ou nao o e-mail — nao revelar quem tem conta
        return ApiResponse.ok(null, "Se o e-mail estiver cadastrado, enviaremos as instrucoes.");
    }

    @PostMapping("/redefinir-senha")
    public ApiResponse<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest req) {
        authService.redefinirSenha(req);
        return ApiResponse.ok(null, "Senha alterada. Faca login com a nova senha.");
    }

    @PostMapping("/aceitar-convite")
    public ApiResponse<Void> aceitarConvite(@Valid @RequestBody AceitarConviteRequest req) {
        authService.aceitarConvite(req);
        return ApiResponse.ok(null, "Cadastro concluido. Faca login.");
    }

    private ResponseEntity<ApiResponse<LoginResponse>> comCookie(AuthService.ResultadoLogin r) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieRefresh(r.refreshTokenBruto(), 7 * 24 * 60 * 60).toString())
                .body(ApiResponse.ok(r.resposta()));
    }

    private ResponseCookie cookieRefresh(String valor, long maxAgeSegundos) {
        return ResponseCookie.from(COOKIE_REFRESH, valor)
                .httpOnly(true)        // JavaScript nao le: XSS nao rouba a sessao
                .secure(true)          // so trafega em HTTPS
                .sameSite("Strict")    // nao viaja em requisicao de outro site (CSRF)
                .path("/api/v1/auth")  // so as rotas que realmente precisam dele
                .maxAge(maxAgeSegundos)
                .build();
    }

    /** IP real do cliente — a app esta atras do Nginx. */
    private String ipDe(HttpServletRequest req) {
        String encaminhado = req.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
