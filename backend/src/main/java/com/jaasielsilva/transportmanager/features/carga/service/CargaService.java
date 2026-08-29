package com.jaasielsilva.transportmanager.features.carga.service;

import com.jaasielsilva.transportmanager.common.AuditoriaService;
import com.jaasielsilva.transportmanager.common.PageResponse;
import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.exception.RegraDeNegocioException;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.Detalhe;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.Resumo;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.SalvarRequest;
import com.jaasielsilva.transportmanager.features.carga.entity.Carga;
import com.jaasielsilva.transportmanager.features.carga.mapper.CargaMapper;
import com.jaasielsilva.transportmanager.features.carga.repository.CargaRepository;
import com.jaasielsilva.transportmanager.features.platform.QuotaService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Molde do kit — features/carga/service/CargaService.java
 *
 * SERVICE DE REFERENCIA. Toda regra de negocio mora aqui; o controller so
 * traduz HTTP. A prova disso e que este arquivo nao importa nada de web.
 *
 * Cinco coisas que todo service de negocio deste padrao repete:
 *   1. quota do plano checada ANTES de criar (409 com caminho de upgrade)
 *   2. duplicidade tratada por excecao de dominio, nunca por try/catch no
 *      controller
 *   3. registro de outro tenant vira 404, nunca 403
 *   4. exclusao logica (deleted_at) + sentinela que libera a chave UNIQUE
 *   5. auditoria do que e destrutivo
 */
@Service
public class CargaService {

    private static final Logger log = LoggerFactory.getLogger(CargaService.class);

    /** Chave em plano_limites. Sem linha la = ilimitado. */
    private static final String QUOTA = "MAX_CADASTROS";

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
        String busca = q == null ? "" : q.trim();
        return PageResponse.of(repository.buscar(busca, pageable), CargaMapper::paraResumo);
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

        var novo = new Carga();
        CargaMapper.aplicar(req, novo);
        // empresaId nao e atribuido aqui: o @TenantId faz o Hibernate gravar o
        // tenant da requisicao. Atribuir a mao permitiria gravar na empresa
        // errada — o unico jeito de furar o isolamento na escrita.
        novo = repository.save(novo);

        log.info("Carga criado: id={} empresa={}", novo.getId(), TenantContext.get());
        return CargaMapper.paraDetalhe(novo);
    }

    @Transactional
    public Detalhe atualizar(Long id, SalvarRequest req) {
        Carga existente = carregar(id);
        garantirDocumentoLivre(req.documento(), id);

        CargaMapper.aplicar(req, existente);
        return CargaMapper.paraDetalhe(repository.save(existente));
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
        alvo.setDeletedAt(LocalDateTime.now());
        alvo.setDeletedSeq(alvo.getId());
        repository.save(alvo);

        auditoriaService.registrar(TenantContext.getObrigatorio(), "EXCLUSAO",
                "carga", id, null);
        log.info("Carga excluido: id={} empresa={}", id, TenantContext.get());
    }

    /**
     * 404 tambem quando o registro e de outra empresa. O @TenantId faz a busca
     * nao encontrar; a mensagem e a mesma de um id inexistente. Devolver 403
     * confirmaria que aquele id existe em algum lugar da plataforma.
     */
    private Carga carregar(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Carga nao encontrado."));
    }

    private void garantirDocumentoLivre(String documento, Long idAtual) {
        if (documento == null || documento.isBlank()) {
            return;
        }
        boolean duplicado = idAtual == null
                ? repository.existsByDocumentoAndDeletedAtIsNull(documento.trim())
                : repository.existsByDocumentoAndDeletedAtIsNullAndIdNot(documento.trim(), idAtual);

        if (duplicado) {
            // 409: o banco tambem barraria pela UNIQUE, mas a mensagem seria
            // "Duplicate entry for key..." — util para o log, inutil na tela.
            throw new RegraDeNegocioException(
                    "Ja existe um cadastro com o documento " + documento.trim() + ".");
        }
    }
}
