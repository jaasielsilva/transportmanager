package com.jaasielsilva.transportmanager.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Molde do kit — common/AuditoriaService.java
 *
 * Registro de acoes criticas: login, exclusao, mudanca de permissao, acoes
 * financeiras, impersonacao, transicoes de assinatura.
 *
 * REQUIRES_NEW de proposito: a auditoria grava mesmo que a transacao de
 * negocio seja revertida depois. Uma tentativa que falhou tambem e informacao
 * — muitas vezes a mais interessante.
 *
 * Nunca gravar aqui senha, token ou PII: audit_logs e uma tabela que muita
 * gente acaba lendo.
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final JdbcTemplate jdbc;

    public AuditoriaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(Long empresaId, String acao, String entidade, Long entidadeId, String ip) {
        registrar(empresaId, null, acao, entidade, entidadeId, null, ip);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long empresaId, Long usuarioId, String acao,
                          String entidade, Long entidadeId, String detalhesJson, String ip) {
        try {
            jdbc.update("""
                    INSERT INTO audit_logs
                        (empresa_id, usuario_id, acao, entidade, entidade_id, detalhes, ip)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, empresaId, usuarioId, acao, entidade, entidadeId, detalhesJson, ip);
        } catch (Exception e) {
            // Auditoria que quebra a operacao principal e pior que auditoria
            // ausente: o cliente perde a acao por causa do registro dela.
            log.error("Falha ao gravar auditoria: acao={} empresa={}", acao, empresaId, e);
        }
    }
}
