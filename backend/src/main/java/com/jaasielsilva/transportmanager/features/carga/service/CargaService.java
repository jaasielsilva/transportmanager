package com.jaasielsilva.transportmanager.features.carga.service;

import com.jaasielsilva.transportmanager.common.AuditoriaService;
import com.jaasielsilva.transportmanager.common.PageResponse;
import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.exception.RegraDeNegocioException;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.AtualizarStatusRequest;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.Detalhe;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.Resumo;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.SalvarRequest;
import com.jaasielsilva.transportmanager.features.carga.entity.Carga;
import com.jaasielsilva.transportmanager.features.carga.mapper.CargaMapper;
import com.jaasielsilva.transportmanager.features.carga.repository.CargaRepository;
import com.jaasielsilva.transportmanager.features.platform.QuotaService;
import java.time.LocalDateTime;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de Cargas de Transporte - Evoluído do CRUD genérico do kit
 * 
 * Lógica específica de transporte:
 * - Validação de status específicos de transporte
 * - Validação de datas e prazos
 * - Fluxo de status da carga
 * - Integração com motoristas e clientes
 */
@Service
public class CargaService {

    private static final Logger log = LoggerFactory.getLogger(CargaService.class);

    /** Chave em plano_limites. Sem linha la = ilimitado. */
    private static final String QUOTA = "MAX_CADASTROS";

    /** Status válidos para cargas de transporte */
    private static final Set<String> STATUS_VALIDOS = Set.of(
        "PENDENTE", "COLETADA", "EM_TRANSITO", "ENTREGUE", "PROBLEMATICA", "CANCELADA"
    );

    private final CargaRepository repository;
    private final QuotaService quotaService;
    private final AuditoriaService auditoriaService;

    public CargaService(CargaRepository repository,
                               QuotaService quotaService,
                               AuditoriaService auditoriaService) {
        this.repository = repository;
        this.quotaService = quotaService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Listagem SEMPRE paginada. O Pageable vem do controller ja com o teto de
     * 100 aplicado — cliente pedindo size=100000 e a forma mais barata de
     * derrubar a aplicacao.
     */
    @Transactional(readOnly = true)
    public PageResponse<Resumo> listar(String q, Pageable pageable) {
        return listar(q, null, null, pageable);
    }

    /**
     * motoristaId/status opcionais: usados por "Minhas entregas"
     * (GET /cargas?motoristaId=X&status=EM_TRANSITO), sem endpoint proprio.
     */
    @Transactional(readOnly = true)
    public PageResponse<Resumo> listar(String q, Long motoristaId, String status, Pageable pageable) {
        String busca = q == null ? "" : q.trim();
        String statusNormalizado = status == null || status.isBlank() ? null : status.toUpperCase().trim();
        return PageResponse.of(
                repository.buscar(busca, motoristaId, statusNormalizado, pageable), CargaMapper::paraResumo);
    }

    @Transactional(readOnly = true)
    public Detalhe buscar(Long id) {
        return CargaMapper.paraDetalhe(carregar(id));
    }

    @Transactional
    public Detalhe criar(SalvarRequest req) {
        // Antes de qualquer escrita: cabe no plano?
        quotaService.exigirCapacidade(QUOTA, repository.countByDeletedAtIsNull());
        garantirDocumentoLivre(req.documento(), null);
        
        // Validações específicas de transporte
        validarStatus(req.status());
        validarDatas(req.dataColeta(), req.dataEntregaPrevista());

        var novo = new Carga();
        CargaMapper.aplicar(req, novo);
        
        // Define status padrão se não informado
        if (novo.getStatus() == null || novo.getStatus().isBlank()) {
            novo.setStatus("PENDENTE");
        }
        
        // empresaId nao e atribuido aqui: o @TenantId faz o Hibernate gravar o
        // tenant da requisicao. Atribuir a mao permitiria gravar na empresa
        // errada — o unico jeito de furar o isolamento na escrita.
        novo = repository.save(novo);

        log.info("Carga criada: id={} empresa={} status={}", novo.getId(), TenantContext.get(), novo.getStatus());
        return CargaMapper.paraDetalhe(novo);
    }

    @Transactional
    public Detalhe atualizar(Long id, SalvarRequest req) {
        Carga existente = carregar(id);
        garantirDocumentoLivre(req.documento(), id);

        // Validações específicas de transporte
        validarStatus(req.status());
        validarDatas(req.dataColeta(), req.dataEntregaPrevista());

        CargaMapper.aplicar(req, existente);
        return CargaMapper.paraDetalhe(repository.save(existente));
    }

    /**
     * Atualiza o status de uma carga seguindo o fluxo de transporte
     */
    @Transactional
    public Detalhe atualizarStatus(Long id, AtualizarStatusRequest req) {
        Carga carga = carregar(id);
        String novoStatus = req.status().toUpperCase().trim();
        
        validarStatus(novoStatus);
        validarTransicaoStatus(carga.getStatus(), novoStatus);
        
        String statusAnterior = carga.getStatus();
        carga.setStatus(novoStatus);
        
        // Atualiza datas automáticas conforme o status
        switch (novoStatus) {
            case "COLETADA" -> {
                if (carga.getDataColeta() == null) {
                    carga.setDataColeta(LocalDateTime.now());
                }
            }
            case "ENTREGUE" -> {
                if (carga.getDataEntregaReal() == null) {
                    carga.setDataEntregaReal(LocalDateTime.now());
                }
            }
        }
        
        repository.save(carga);
        
        auditoriaService.registrar(TenantContext.getObrigatorio(), "MUDANCA_STATUS",
                "carga", id, String.format("%s -> %s", statusAnterior, novoStatus));
        
        log.info("Status da carga atualizado: id={} {} -> {}", id, statusAnterior, novoStatus);
        return CargaMapper.paraDetalhe(carga);
    }

    /**
     * Exclusao logica. O dado continua no banco: "apaguei sem querer" e uma
     * ligacao de suporte comum, e restaurar precisa ser um UPDATE.
     *
     * deletedSeq recebe o id para liberar a chave UNIQUE (empresa_id,
     * documento, deleted_seq) — sem isso o cliente nao consegue recadastrar o
     * mesmo documento depois de excluir.
     */
    @Transactional
    public void excluir(Long id) {
        Carga alvo = carregar(id);
        
        // Não permite excluir cargas em trânsito
        if ("EM_TRANSITO".equals(alvo.getStatus())) {
            throw new RegraDeNegocioException(
                "Não é possível excluir uma carga em trânsito. Cancele a carga primeiro.");
        }
        
        alvo.setDeletedAt(LocalDateTime.now());
        alvo.setDeletedSeq(alvo.getId());
        repository.save(alvo);

        auditoriaService.registrar(TenantContext.getObrigatorio(), "EXCLUSAO",
                "carga", id, null);
        log.info("Carga excluída: id={} empresa={}", id, TenantContext.get());
    }

    /**
     * 404 tambem quando o registro e de outra empresa. O @TenantId faz a busca
     * nao encontrar; a mensagem e a mesma de um id inexistente. Devolver 403
     * confirmaria que aquele id existe em algum lugar da plataforma.
     */
    private Carga carregar(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Carga não encontrada."));
    }

    private void garantirDocumentoLivre(String documento, Long idAtual) {
        if (documento == null || documento.isBlank()) {
            return;
        }
        boolean duplicado = idAtual == null
                ? repository.existsByDocumentoAndDeletedAtIsNull(documento.trim())
                : repository.existsByDocumentoAndDeletedAtIsNullAndIdNot(documento.trim(), idAtual);

        if (duplicado) {
            throw new RegraDeNegocioException(
                    "Já existe um cadastro com o documento " + documento.trim() + ".");
        }
    }

    private void validarStatus(String status) {
        if (status != null && !status.isBlank()) {
            String statusNormalizado = status.toUpperCase().trim();
            if (!STATUS_VALIDOS.contains(statusNormalizado)) {
                throw new RegraDeNegocioException(
                    "Status inválido. Status válidos: " + String.join(", ", STATUS_VALIDOS));
            }
        }
    }

    private void validarDatas(LocalDateTime dataColeta, LocalDateTime dataEntregaPrevista) {
        if (dataColeta != null && dataEntregaPrevista != null) {
            if (dataEntregaPrevista.isBefore(dataColeta)) {
                throw new RegraDeNegocioException(
                    "A data de entrega prevista não pode ser anterior à data de coleta.");
            }
        }
    }

    private void validarTransicaoStatus(String statusAtual, String novoStatus) {
        // Transições válidas de status
        switch (statusAtual) {
            case "PENDENTE" -> {
                if (!Set.of("COLETADA", "CANCELADA").contains(novoStatus)) {
                    throw new RegraDeNegocioException(
                        "De PENDENTE só é possível ir para COLETADA ou CANCELADA.");
                }
            }
            case "COLETADA" -> {
                if (!Set.of("EM_TRANSITO", "PROBLEMATICA", "CANCELADA").contains(novoStatus)) {
                    throw new RegraDeNegocioException(
                        "De COLETADA só é possível ir para EM_TRANSITO, PROBLEMATICA ou CANCELADA.");
                }
            }
            case "EM_TRANSITO" -> {
                if (!Set.of("ENTREGUE", "PROBLEMATICA").contains(novoStatus)) {
                    throw new RegraDeNegocioException(
                        "De EM_TRANSITO só é possível ir para ENTREGUE ou PROBLEMATICA.");
                }
            }
            case "PROBLEMATICA" -> {
                if (!Set.of("EM_TRANSITO", "CANCELADA").contains(novoStatus)) {
                    throw new RegraDeNegocioException(
                        "De PROBLEMATICA só é possível voltar para EM_TRANSITO ou ir para CANCELADA.");
                }
            }
            case "ENTREGUE", "CANCELADA" -> {
                throw new RegraDeNegocioException(
                    "Não é possível alterar o status de uma carga " + statusAtual + ".");
            }
        }
    }
}
