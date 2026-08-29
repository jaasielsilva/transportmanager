package com.jaasielsilva.transportmanager.features.carga.entity;

import com.jaasielsilva.transportmanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.TenantId;

/**
 * Molde do kit — features/carga/entity/Carga.java
 *
 * ENTITY DE REFERENCIA. Toda entity de negocio do projeto se parece com esta;
 * copie a estrutura em vez de inventar outra.
 *
 * Tres coisas aqui nao sao decoracao:
 *
 * 1. {@code @TenantId} no empresaId — o Hibernate passa a filtrar findAll(),
 *    JPQL e derived queries por empresa_id sozinho. Esquecer o filtro deixa de
 *    ser possivel. A coluna vira imutavel, o que esta certo: um registro nunca
 *    muda de empresa.
 *
 * 2. deletedAt — exclusao e logica. O cliente que apagou por engano liga no dia
 *    seguinte, e "restaurar" precisa ser um UPDATE, nao um restore de backup.
 *
 * 3. deletedSeq — a sentinela que faz UNIQUE e soft delete conviverem. Com
 *    UNIQUE (empresa_id, documento) puro, um registro excluido continua
 *    ocupando o documento e o cliente nao consegue recadastrar. O service
 *    grava o proprio id aqui ao excluir, entao cada versao excluida ocupa uma
 *    combinacao diferente e so a viva (deletedSeq = 0) disputa a chave.
 */
@Entity
@Table(name = "cargas")
public class Carga extends BaseEntity {

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String telefone;

    /** Documento do cadastro (CPF/CNPJ). Unico por empresa, nunca global. */
    @Column(length = 20)
    private String documento;

    @Column(length = 500)
    private String observacao;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 0 enquanto vivo; recebe o proprio id na exclusao (ver javadoc da classe). */
    @Column(name = "deleted_seq", nullable = false)
    private long deletedSeq;

    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long v) { this.empresaId = v; }
    public String getNome() { return nome; }
    public void setNome(String v) { this.nome = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String v) { this.telefone = v; }
    public String getDocumento() { return documento; }
    public void setDocumento(String v) { this.documento = v; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String v) { this.observacao = v; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean v) { this.ativo = v; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
    public long getDeletedSeq() { return deletedSeq; }
    public void setDeletedSeq(long v) { this.deletedSeq = v; }
}
