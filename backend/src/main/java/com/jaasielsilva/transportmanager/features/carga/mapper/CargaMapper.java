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
        return new Resumo(e.getId(), e.getNome(), e.getEmail(), e.getTelefone(), e.isAtivo());
    }

    public static Detalhe paraDetalhe(Carga e) {
        return new Detalhe(
                e.getId(), e.getNome(), e.getEmail(), e.getTelefone(),
                e.getDocumento(), e.getObservacao(), e.isAtivo(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    /** Copia o request para a entity — criacao e edicao usam o mesmo caminho. */
    public static void aplicar(SalvarRequest req, Carga destino) {
        destino.setNome(req.nome().trim());
        destino.setEmail(normalizar(req.email()));
        destino.setTelefone(normalizar(req.telefone()));
        destino.setDocumento(normalizar(req.documento()));
        destino.setObservacao(normalizar(req.observacao()));
        // Ausente no request = mantem o valor atual; na criacao, ativo.
        if (req.ativo() != null) {
            destino.setAtivo(req.ativo());
        }
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
