package com.jaasielsilva.transportmanager.features.motorista.entity;

import com.jaasielsilva.transportmanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.TenantId;

/**
 * Entity de Motorista — copiada da estrutura de Carga (molde do kit).
 *
 * CRUD simples, sem maquina de estados: motorista so tem cadastro e
 * ativo/inativo. usuarioId (nullable) e o que liga o cadastro a um login —
 * sem ele o motorista existe no cadastro mas nao acessa "Minhas entregas".
 */
@Entity
@Table(name = "motoristas")
public class Motorista extends BaseEntity {

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 20)
    private String cnh;

    @Column(length = 20)
    private String telefone;

    @Column(length = 150)
    private String email;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_seq", nullable = false)
    private long deletedSeq;

    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long v) { this.empresaId = v; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long v) { this.usuarioId = v; }
    public String getNome() { return nome; }
    public void setNome(String v) { this.nome = v; }
    public String getCnh() { return cnh; }
    public void setCnh(String v) { this.cnh = v; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String v) { this.telefone = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean v) { this.ativo = v; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
    public long getDeletedSeq() { return deletedSeq; }
    public void setDeletedSeq(long v) { this.deletedSeq = v; }
}
