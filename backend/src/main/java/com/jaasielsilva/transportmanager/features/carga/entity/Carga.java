package com.jaasielsilva.transportmanager.features.carga.entity;

import com.jaasielsilva.transportmanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.TenantId;

/**
 * Entity de Cargas de Transporte - Evoluída do CRUD genérico do kit
 * 
 * Campos específicos de logística adicionados:
 * - Endereços de origem e destino
 * - Peso e valor do frete
 * - Status específicos de transporte
 * - Integração com motoristas e clientes
 * - Prazos e rastreamento
 */
@Entity
@Table(name = "cargas")
public class Carga extends BaseEntity {

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    // Campos originais do CRUD genérico (mantidos para compatibilidade)
    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(length = 20)
    private String documento;

    @Column(length = 500)
    private String observacao;

    @Column(nullable = false)
    private boolean ativo = true;

    // Campos específicos de transporte
    @Column(name = "origem_endereco", length = 255)
    private String origemEndereco;

    @Column(name = "origem_cidade", length = 100)
    private String origemCidade;

    @Column(name = "origem_uf", length = 2)
    private String origemUf;

    @Column(name = "destino_endereco", length = 255)
    private String destinoEndereco;

    @Column(name = "destino_cidade", length = 100)
    private String destinoCidade;

    @Column(name = "destino_uf", length = 2)
    private String destinoUf;

    @Column(precision = 10, scale = 2)
    private BigDecimal peso;

    @Column(name = "valor_frete", precision = 12, scale = 2)
    private BigDecimal valorFrete;

    @Column(length = 20)
    private String status = "PENDENTE";

    @Column(name = "motorista_id")
    private Long motoristaId;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "data_coleta")
    private LocalDateTime dataColeta;

    @Column(name = "data_entrega_prevista")
    private LocalDateTime dataEntregaPrevista;

    @Column(name = "data_entrega_real")
    private LocalDateTime dataEntregaReal;

    @Column(name = "distancia_km")
    private Integer distanciaKm;

    @Column(name = "tempo_estimado_minutos")
    private Integer tempoEstimadoMinutos;

    // Campos de soft delete (padrão do kit)
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_seq", nullable = false)
    private long deletedSeq;

    // Getters e Setters dos campos originais
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

    // Getters e Setters dos campos específicos de transporte
    public String getOrigemEndereco() { return origemEndereco; }
    public void setOrigemEndereco(String v) { this.origemEndereco = v; }
    public String getOrigemCidade() { return origemCidade; }
    public void setOrigemCidade(String v) { this.origemCidade = v; }
    public String getOrigemUf() { return origemUf; }
    public void setOrigemUf(String v) { this.origemUf = v; }
    public String getDestinoEndereco() { return destinoEndereco; }
    public void setDestinoEndereco(String v) { this.destinoEndereco = v; }
    public String getDestinoCidade() { return destinoCidade; }
    public void setDestinoCidade(String v) { this.destinoCidade = v; }
    public String getDestinoUf() { return destinoUf; }
    public void setDestinoUf(String v) { this.destinoUf = v; }
    public BigDecimal getPeso() { return peso; }
    public void setPeso(BigDecimal v) { this.peso = v; }
    public BigDecimal getValorFrete() { return valorFrete; }
    public void setValorFrete(BigDecimal v) { this.valorFrete = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getMotoristaId() { return motoristaId; }
    public void setMotoristaId(Long v) { this.motoristaId = v; }
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long v) { this.clienteId = v; }
    public LocalDateTime getDataColeta() { return dataColeta; }
    public void setDataColeta(LocalDateTime v) { this.dataColeta = v; }
    public LocalDateTime getDataEntregaPrevista() { return dataEntregaPrevista; }
    public void setDataEntregaPrevista(LocalDateTime v) { this.dataEntregaPrevista = v; }
    public LocalDateTime getDataEntregaReal() { return dataEntregaReal; }
    public void setDataEntregaReal(LocalDateTime v) { this.dataEntregaReal = v; }
    public Integer getDistanciaKm() { return distanciaKm; }
    public void setDistanciaKm(Integer v) { this.distanciaKm = v; }
    public Integer getTempoEstimadoMinutos() { return tempoEstimadoMinutos; }
    public void setTempoEstimadoMinutos(Integer v) { this.tempoEstimadoMinutos = v; }
}
