package com.jaasielsilva.transportmanager.features.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Molde do kit — features/auth/TokenAcesso.java
 *
 * Convite de usuario e reset de senha na mesma tabela: mesmo ciclo de vida
 * (uso unico, expiravel) e mesmo cuidado (guardamos o hash, nunca o valor
 * enviado por e-mail).
 */
@Entity
@Table(name = "tokens_acesso")
public class TokenAcesso {

    public enum Tipo { CONVITE, RESET_SENHA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isValido() {
        return usadoEm == null && expiraEm.isAfter(LocalDateTime.now());
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long v) { this.usuarioId = v; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo v) { this.tipo = v; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String v) { this.tokenHash = v; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime v) { this.expiraEm = v; }
    public LocalDateTime getUsadoEm() { return usadoEm; }
    public void setUsadoEm(LocalDateTime v) { this.usadoEm = v; }
}
