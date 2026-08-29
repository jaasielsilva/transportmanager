package com.jaasielsilva.transportmanager.features.auth;

import com.jaasielsilva.transportmanager.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Molde do kit — features/auth/Usuario.java
 *
 * ATENCAO — esta entity NAO leva @TenantId, de proposito.
 *
 * O login acontece ANTES de existir tenant: quando o e-mail chega, ainda nao se
 * sabe de que empresa ele e. Com @TenantId o Hibernate filtraria a busca pelo
 * tenant "sem tenant" e nenhum usuario seria encontrado — ninguem conseguiria
 * entrar no sistema.
 *
 * Por isso o e-mail e unico GLOBALMENTE aqui, e nao (empresa_id, email):
 * usuarios e tabela de AUTENTICACAO, nao de negocio. A regra "UNIQUE sempre
 * composto com empresa_id" continua valendo para as tabelas de negocio.
 *
 * Consequencia assumida: uma pessoa pertence a uma empresa so. Se um dia
 * precisar pertencer a varias, isso vira uma tabela usuario_empresas mais um
 * seletor de empresa apos o login — decisao para registrar em ADR quando
 * acontecer, nao para antecipar agora.
 */
@Entity
@Table(name = "usuarios")
public class Usuario extends BaseEntity {

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    /** null ate o convidado aceitar o convite e definir a propria senha. */
    @Column(name = "senha_hash", length = 100)
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "ultimo_login_em")
    private LocalDateTime ultimoLoginEm;

    // --- Rate limiting do login ---
    @Column(name = "tentativas_falhas", nullable = false)
    private short tentativasFalhas;

    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_roles", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "role", nullable = false)
    private Set<String> roles = new HashSet<>();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** true enquanto o bloqueio por tentativas falhas estiver valendo. */
    public boolean isBloqueado() {
        return bloqueadoAte != null && bloqueadoAte.isAfter(LocalDateTime.now());
    }

    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long v) { this.empresaId = v; }
    public String getNome() { return nome; }
    public void setNome(String v) { this.nome = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String v) { this.senhaHash = v; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean v) { this.ativo = v; }
    public LocalDateTime getUltimoLoginEm() { return ultimoLoginEm; }
    public void setUltimoLoginEm(LocalDateTime v) { this.ultimoLoginEm = v; }
    public short getTentativasFalhas() { return tentativasFalhas; }
    public void setTentativasFalhas(short v) { this.tentativasFalhas = v; }
    public LocalDateTime getBloqueadoAte() { return bloqueadoAte; }
    public void setBloqueadoAte(LocalDateTime v) { this.bloqueadoAte = v; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> v) { this.roles = v; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
}
