package com.jaasielsilva.transportmanager.features.carga.mapper;

import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.Detalhe;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.Resumo;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.SalvarRequest;
import com.jaasielsilva.transportmanager.features.carga.entity.Carga;

/**
 * Molde do kit — features/carga/mapper/CargaMapper.java
 *
 * Conversao a mao, sem biblioteca de mapeamento. Com DTO pequeno o mapeamento
 * automatico troca 10 linhas obvias por uma dependencia, geracao em build e um
 * erro que so aparece em runtime quando alguem renomeia um campo.
 *
 * aplicar() nunca toca em id, empresaId, deletedAt nem nas datas: sao do
 * sistema, e um request nao deve conseguir mexer neles nem por engano.
 */
public final class CargaMapper {

    private CargaMapper() {}

    public static Resumo paraResumo(Carga e) {
        return new Resumo(
            e.getId(), 
            e.getNome(), 
            e.getStatus(),
            e.getOrigemCidade(),
            e.getOrigemUf(),
            e.getDestinoCidade(),
            e.getDestinoUf(),
            e.getValorFrete(),
            e.isAtivo()
        );
    }

    public static Detalhe paraDetalhe(Carga e) {
        return new Detalhe(
                e.getId(), e.getNome(), e.getEmail(), e.getTelefone(),
                e.getDocumento(), e.getObservacao(), e.isAtivo(),
                e.getCreatedAt(), e.getUpdatedAt(),
                // Campos específicos de transporte
                e.getOrigemEndereco(),
                e.getOrigemCidade(),
                e.getOrigemUf(),
                e.getDestinoEndereco(),
                e.getDestinoCidade(),
                e.getDestinoUf(),
                e.getPeso(),
                e.getValorFrete(),
                e.getStatus(),
                e.getMotoristaId(),
                e.getClienteId(),
                e.getDataColeta(),
                e.getDataEntregaPrevista(),
                e.getDataEntregaReal(),
                e.getDistanciaKm(),
                e.getTempoEstimadoMinutos()
        );
    }

    /** Copia o request para a entity — criacao e edicao usam o mesmo caminho. */
    public static void aplicar(SalvarRequest req, Carga destino) {
        // Campos originais do CRUD genérico
        destino.setNome(req.nome().trim());
        destino.setEmail(normalizar(req.email()));
        destino.setTelefone(normalizar(req.telefone()));
        destino.setDocumento(normalizar(req.documento()));
        destino.setObservacao(normalizar(req.observacao()));
        if (req.ativo() != null) {
            destino.setAtivo(req.ativo());
        }

        // Campos específicos de transporte
        destino.setOrigemEndereco(normalizar(req.origemEndereco()));
        destino.setOrigemCidade(normalizar(req.origemCidade()));
        destino.setOrigemUf(normalizar(req.origemUf()));
        destino.setDestinoEndereco(normalizar(req.destinoEndereco()));
        destino.setDestinoCidade(normalizar(req.destinoCidade()));
        destino.setDestinoUf(normalizar(req.destinoUf()));
        destino.setPeso(req.peso());
        destino.setValorFrete(req.valorFrete());
        // Status so muda se vier preenchido: na edicao o form nao envia status
        // (a troca tem endpoint proprio com validacao de transicao), e null
        // aqui apagaria o status atual.
        if (req.status() != null && !req.status().isBlank()) {
            destino.setStatus(normalizar(req.status()));
        }
        destino.setMotoristaId(req.motoristaId());
        destino.setClienteId(req.clienteId());
        destino.setDataColeta(req.dataColeta());
        destino.setDataEntregaPrevista(req.dataEntregaPrevista());
        destino.setDataEntregaReal(req.dataEntregaReal());
        destino.setDistanciaKm(req.distanciaKm());
        destino.setTempoEstimadoMinutos(req.tempoEstimadoMinutos());
    }

    /**
     * String vazia vira null. Sem isto, "" e null convivem na mesma coluna e
     * toda consulta passa a precisar tratar os dois casos — inclusive a chave
     * UNIQUE (empresa_id, documento), onde varios "" colidem entre si.
     */
    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
