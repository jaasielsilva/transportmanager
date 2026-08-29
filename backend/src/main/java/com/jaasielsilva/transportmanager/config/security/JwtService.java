package com.jaasielsilva.transportmanager.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Molde do kit — config/security/JwtService.java
 *
 * Access token curto (15 min) e assinado; refresh token opaco e persistido.
 * A divisao existe porque JWT nao se revoga: se o access valesse dias, um
 * token vazado valeria dias. Curto + refresh revogavel resolve os dois lados.
 *
 * Claims: sub (userId), empresaId (o tenant), roles e modules.
 * empresaId sair do TOKEN e nao do body e o que sustenta o isolamento inteiro.
 */
@Service
public class JwtService {

    private final SecretKey chave;
    private final long minutosAccessToken;
    private final long diasRefreshToken;
    private final SecureRandom random = new SecureRandom();

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-token-minutes:15}") long minutosAccessToken,
                      @Value("${app.jwt.refresh-token-days:7}") long diasRefreshToken) {

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // Falhar na subida e melhor que rodar com assinatura fraca em producao.
            throw new IllegalStateException(
                    "app.jwt.secret precisa de no minimo 32 bytes. Gere com: openssl rand -base64 64");
        }
        this.chave = Keys.hmacShaKeyFor(bytes);
        this.minutosAccessToken = minutosAccessToken;
        this.diasRefreshToken = diasRefreshToken;
    }

    public String gerarAccessToken(Long usuarioId, Long empresaId, Set<String> roles,
                                   List<String> modulos) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("empresaId", empresaId)
                .claim("roles", roles)
                // Mudou de plano? Vale no proximo refresh — no maximo 15 min de defasagem.
                .claim("modules", modulos)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(minutosAccessToken, ChronoUnit.MINUTES)))
                .signWith(chave)
                .compact();
    }

    /** Token de refresh opaco — valor aleatorio, sem significado, impossivel de forjar. */
    public String gerarRefreshTokenBruto() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** No banco guardamos so o hash: vazamento da tabela nao vira sessao sequestrada. */
    public String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    public Instant expiracaoDoRefresh() {
        return Instant.now().plus(diasRefreshToken, ChronoUnit.DAYS);
    }

    /** Informado ao front para ele agendar o refresh antes de o token expirar. */
    public long segundosDoAccessToken() {
        return minutosAccessToken * 60;
    }

    /** @return claims validas, ou null se o token for invalido/expirado. */
    public Claims validar(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;   // nunca logar o token
        }
    }
}
