package com.jaasielsilva.transportmanager.features.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Molde do kit — features/auth/RefreshToken.java
 *
 * Opaco e persistido para poder ser REVOGADO — e a diferenca entre "o usuario
 * saiu" e "o usuario continua entrando com um token que voce nao controla".
 *
 * Guardamos o SHA-256, nunca o token em claro: vazamento do banco nao vira
 * sequestro de sessao.
 *
 * substituidoPor forma a cadeia de rotacao. Se um token ja substituido for
 * usado de novo, alguem copiou o cookie — a resposta certa e revogar a cadeia
 * inteira daquele usuario, nao so aquele token.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "revogado_em")
    private LocalDateTime revogadoEm;

    @Column(name = "substituido_por")
    private Long substituidoPor;

    @Column(length = 45)
    private String ip;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isValido() {
        return revogadoEm == null && expiraEm.isAfter(LocalDateTime.now());
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long v) { this.usuarioId = v; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String v) { this.tokenHash = v; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime v) { this.expiraEm = v; }
    public LocalDateTime getRevogadoEm() { return revogadoEm; }
    public void setRevogadoEm(LocalDateTime v) { this.revogadoEm = v; }
    public Long getSubstituidoPor() { return substituidoPor; }
    public void setSubstituidoPor(Long v) { this.substituidoPor = v; }
    public String getIp() { return ip; }
    public void setIp(String v) { this.ip = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
