package com.jaasielsilva.transportmanager.features.platform;

import com.jaasielsilva.transportmanager.common.AuditoriaService;
import com.jaasielsilva.transportmanager.common.PageResponse;
import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.features.billing.AssinaturaService;
import com.jaasielsilva.transportmanager.features.billing.AssinaturaStatus;
import com.jaasielsilva.transportmanager.features.billing.DunningEventoRepository;
import com.jaasielsilva.transportmanager.features.billing.WebhookEventoService;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Molde do kit — features/platform/PlatformTenantService.java
 *
 * Operacoes do dono do SaaS sobre os tenants. Tudo aqui atravessa empresas de
 * proposito — e um dos tres lugares onde isso e legitimo.
 *
 * Consulta em SQL direto (JdbcTemplate), como no PlatformMetricsService: sao
 * leituras de RELATORIO cruzando empresas, planos, usuarios e limites. Forcar
 * JPQL nisso renderia N+1 e um punhado de entidades que existiriam so para
 * alimentar uma tela administrativa.
 */
@Service
public class PlatformTenantService {

    /** Situacao "derivada": nao existe status assim, e uma janela de tempo. */
    private static final String TRIAL_EXPIRANDO = "TRIAL_EXPIRANDO";

    private final JdbcTemplate jdbc;
    private final EmpresaRepository empresaRepository;
    private final DunningEventoRepository dunningEventoRepository;
    private final AuditoriaService auditoriaService;
    private final WebhookEventoService webhookEventoService;
    private final AssinaturaService assinaturaService;

    /**
     * Todas as features que sabem contar consumo de alguma quota. O Spring
     * injeta a lista inteira: feature nova com limite entra no painel sozinha,
     * sem tocar nesta classe.
     */
    private final Map<String, ConsumoDeQuota> consumos;

    public PlatformTenantService(JdbcTemplate jdbc,
                                 EmpresaRepository empresaRepository,
                                 DunningEventoRepository dunningEventoRepository,
                                 AuditoriaService auditoriaService,
                                 WebhookEventoService webhookEventoService,
                                 AssinaturaService assinaturaService,
                                 List<ConsumoDeQuota> consumos) {
        this.jdbc = jdbc;
        this.empresaRepository = empresaRepository;
        this.dunningEventoRepository = dunningEventoRepository;
        this.auditoriaService = auditoriaService;
        this.webhookEventoService = webhookEventoService;
        this.assinaturaService = assinaturaService;
        this.consumos = consumos.stream()
                .collect(Collectors.toMap(ConsumoDeQuota::chave, Function.identity()));
    }

    // =================================================================
    // Listagem
    // =================================================================

    /**
     * @param situacao ACTIVE | TRIALING | PAST_DUE | CANCELED | TRIAL_EXPIRANDO
     * @param q        busca por razao social ou documento
     */
    public PageResponse<PlatformDtos.TenantResumo> listar(String situacao, String q, Pageable pageable) {
        var filtro = new StringBuilder(" WHERE e.deleted_at IS NULL ");
        var params = new ArrayList<Object>();

        if (TRIAL_EXPIRANDO.equalsIgnoreCase(situacao)) {
            filtro.append("""
                     AND e.assinatura_status = 'TRIALING'
                     AND e.trial_expira_em BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)
                    """);
        } else if (situacao != null && !situacao.isBlank()) {
            // Parametro, nunca concatenacao: e texto vindo da query string.
            filtro.append(" AND e.assinatura_status = ? ");
            params.add(situacao.toUpperCase());
        }

        if (q != null && !q.isBlank()) {
            filtro.append(" AND (e.razao_social LIKE ? OR e.documento LIKE ?) ");
            params.add("%" + q.trim() + "%");
            params.add("%" + q.trim() + "%");
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM empresas e" + filtro, Long.class, params.toArray());
        long totalElements = total == null ? 0 : total;

        int size = Math.min(pageable.getPageSize(), 100);
        int page = pageable.getPageNumber();

        var paramsPagina = new ArrayList<>(params);
        paramsPagina.add(size);
        paramsPagina.add((long) page * size);

        // A ORDEM nao vem do cliente, e fixa e opinativa: primeiro quem esta
        // dando problema (atraso), depois quem pode dar (trial). O painel
        // existe para mostrar o que precisa de acao hoje.
        List<PlatformDtos.TenantResumo> conteudo = jdbc.query("""
                SELECT e.id, e.razao_social, p.nome AS plano, e.assinatura_status,
                       e.dunning_etapa, e.trial_expira_em,
                       (SELECT COUNT(*) FROM usuarios u
                         WHERE u.empresa_id = e.id AND u.deleted_at IS NULL) AS usuarios,
                       (SELECT MAX(u2.ultimo_login_em) FROM usuarios u2
                         WHERE u2.empresa_id = e.id) AS ultimo_acesso
                  FROM empresas e
                  JOIN planos p ON p.id = e.plano_id
                """ + filtro + """
                 ORDER BY FIELD(e.assinatura_status, 'PAST_DUE', 'TRIALING', 'ACTIVE', 'CANCELED'),
                          e.created_at DESC
                 LIMIT ? OFFSET ?
                """, (rs, i) -> new PlatformDtos.TenantResumo(
                        rs.getLong("id"),
                        rs.getString("razao_social"),
                        rs.getString("plano"),
                        rs.getString("assinatura_status"),
                        rs.getInt("dunning_etapa"),
                        dia(rs.getDate("trial_expira_em")),
                        rs.getLong("usuarios"),
                        data(rs.getTimestamp("ultimo_acesso"))),
                paramsPagina.toArray());

        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(conteudo, page, size, totalElements, totalPages);
    }

    // =================================================================
    // Ficha do tenant
    // =================================================================

    public PlatformDtos.TenantDetalhe detalhar(Long id) {
        Map<String, Object> r;
        try {
            r = jdbc.queryForMap("""
                    SELECT e.id, e.razao_social, e.documento, e.plano_id,
                           p.nome AS plano, p.preco_mensal,
                           e.assinatura_status, e.dunning_etapa, e.past_due_desde,
                           e.trial_expira_em, e.ativada_em, e.purge_em,
                           (SELECT COUNT(*) FROM usuarios u
                             WHERE u.empresa_id = e.id AND u.deleted_at IS NULL) AS usuarios
                      FROM empresas e
                      JOIN planos p ON p.id = e.plano_id
                     WHERE e.id = ?
                    """, id);
        } catch (EmptyResultDataAccessException e) {
            throw new RecursoNaoEncontradoException("Tenant nao encontrado.");
        }

        return new PlatformDtos.TenantDetalhe(
                id,
                (String) r.get("razao_social"),
                (String) r.get("documento"),
                (String) r.get("plano"),
                (BigDecimal) r.get("preco_mensal"),
                (String) r.get("assinatura_status"),
                ((Number) r.get("dunning_etapa")).intValue(),
                assinaturaService.nivelDeAcessoDe(id).name(),
                data((Timestamp) r.get("past_due_desde")),
                dia((Date) r.get("trial_expira_em")),
                data((Timestamp) r.get("ativada_em")),
                dia((Date) r.get("purge_em")),
                ((Number) r.get("usuarios")).longValue(),
                limitesDoPlano(((Number) r.get("plano_id")).longValue(), id));
    }

    /**
     * Limite do plano x consumo atual. E a conversa de upgrade acontecendo
     * ANTES de o cliente esbarrar no 409 — ali ja e tarde: ele esta travado.
     *
     * Chave sem ConsumoDeQuota correspondente aparece com consumo -1 em vez de
     * sumir da lista: quota configurada que ninguem mede e um problema a
     * resolver, nao um detalhe a esconder.
     */
    private List<PlatformDtos.LimiteConsumo> limitesDoPlano(Long planoId, Long empresaId) {
        return jdbc.query("""
                SELECT chave, valor FROM plano_limites WHERE plano_id = ? ORDER BY chave
                """, (rs, i) -> {
            String chave = rs.getString("chave");
            ConsumoDeQuota medidor = consumos.get(chave);
            long consumo = medidor == null
                    ? -1
                    : TenantContext.semFiltroDeTenant(() -> medidor.consumoDe(empresaId));
            return new PlatformDtos.LimiteConsumo(chave, rs.getLong("valor"), consumo);
        }, planoId);
    }

    public List<PlatformDtos.DunningEvento> historicoDeDunning(Long empresaId) {
        return TenantContext.semFiltroDeTenant(
                () -> dunningEventoRepository.findByEmpresaIdOrderByCreatedAtDesc(empresaId)
                        .stream()
                        .map(d -> new PlatformDtos.DunningEvento(
                                d.getEtapa(), d.getAcao(), d.getDetalhes(), d.getCreatedAt()))
                        .toList());
    }

    // =================================================================
    // Acoes operacionais
    // =================================================================

    /**
     * Concessao manual de prazo: empurra o inicio do atraso para frente, sem
     * marcar como pago. Existe porque cliente grande negocia — e sem este
     * botao a alternativa e um UPDATE na mao em producao.
     */
    @Transactional
    public void prorrogarCobranca(Long empresaId, int dias) {
        TenantContext.semFiltroDeTenant(() -> {
            var empresa = empresaRepository.findById(empresaId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Tenant nao encontrado."));

            if (empresa.getAssinaturaStatus() != AssinaturaStatus.PAST_DUE) {
                return null;
            }
            empresa.setPastDueDesde(empresa.getPastDueDesde().plusDays(dias));
            empresa.setDunningEtapa(0);          // volta ao inicio da regua
            empresaRepository.save(empresa);

            auditoriaService.registrar(empresaId, "DUNNING_PRORROGADO",
                    "empresa", empresaId, null);
            return null;
        });
    }

    /**
     * Eventos do gateway que ainda nao foram processados. Sem esta lista, usar
     * o botao de reprocessar exigiria descobrir o id do evento no banco de
     * producao — ou seja, ele nao seria usado.
     */
    public List<PlatformDtos.WebhookFalha> webhooksPendentes() {
        return jdbc.query("""
                SELECT evento_id, tipo, erro, created_at
                  FROM webhook_eventos
                 WHERE processado_em IS NULL
                 ORDER BY created_at DESC
                 LIMIT 50
                """, (rs, i) -> new PlatformDtos.WebhookFalha(
                        rs.getString("evento_id"),
                        rs.getString("tipo"),
                        rs.getString("erro"),
                        data(rs.getTimestamp("created_at"))));
    }

    /** Reprocessa um evento do gateway que falhou, a partir do payload guardado. */
    public void reprocessarWebhook(String eventoId) {
        webhookEventoService.reprocessar(eventoId);
        auditoriaService.registrar(null, "WEBHOOK_REPROCESSADO", "webhook_evento", null, null);
    }

    private static LocalDateTime data(Timestamp valor) {
        return valor == null ? null : valor.toLocalDateTime();
    }

    private static LocalDate dia(Date valor) {
        return valor == null ? null : valor.toLocalDate();
    }
}
