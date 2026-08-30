package com.jaasielsilva.transportmanager.features.motorista.mapper;

import com.jaasielsilva.transportmanager.features.motorista.dto.MotoristaDtos.Detalhe;
import com.jaasielsilva.transportmanager.features.motorista.dto.MotoristaDtos.Resumo;
import com.jaasielsilva.transportmanager.features.motorista.dto.MotoristaDtos.SalvarRequest;
import com.jaasielsilva.transportmanager.features.motorista.entity.Motorista;

/** Molde do kit — features/carga/mapper/CargaMapper.java. Conversao a mao, sem lib. */
public final class MotoristaMapper {

    private MotoristaMapper() {}

    public static Resumo paraResumo(Motorista e) {
        return new Resumo(e.getId(), e.getNome(), e.getTelefone(), e.isAtivo());
    }

    public static Detalhe paraDetalhe(Motorista e) {
        return new Detalhe(
                e.getId(), e.getNome(), e.getCnh(), e.getTelefone(), e.getEmail(),
                e.getUsuarioId(), e.isAtivo(), e.getCreatedAt(), e.getUpdatedAt());
    }

    /** aplicar() nunca toca em id, empresaId nem deletedAt. */
    public static void aplicar(SalvarRequest req, Motorista destino) {
        destino.setNome(req.nome().trim());
        destino.setCnh(normalizar(req.cnh()));
        destino.setTelefone(normalizar(req.telefone()));
        destino.setEmail(normalizar(req.email()));
        destino.setUsuarioId(req.usuarioId());
        if (req.ativo() != null) {
            destino.setAtivo(req.ativo());
        }
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
