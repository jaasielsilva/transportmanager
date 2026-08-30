package com.jaasielsilva.transportmanager.features.motorista.service;

import com.jaasielsilva.transportmanager.common.AuditoriaService;
import com.jaasielsilva.transportmanager.common.PageResponse;
import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.exception.RegraDeNegocioException;
import com.jaasielsilva.transportmanager.features.motorista.dto.MotoristaDtos.Detalhe;
import com.jaasielsilva.transportmanager.features.motorista.dto.MotoristaDtos.Resumo;
import com.jaasielsilva.transportmanager.features.motorista.dto.MotoristaDtos.SalvarRequest;
import com.jaasielsilva.transportmanager.features.motorista.entity.Motorista;
import com.jaasielsilva.transportmanager.features.motorista.mapper.MotoristaMapper;
import com.jaasielsilva.transportmanager.features.motorista.repository.MotoristaRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Molde do kit — features/carga/service/CargaService.java. CRUD simples, sem
 * maquina de estados (motorista so tem cadastro e ativo/inativo).
 */
@Service
public class MotoristaService {

    private static final Logger log = LoggerFactory.getLogger(MotoristaService.class);

    private final MotoristaRepository repository;
    private final AuditoriaService auditoriaService;

    public MotoristaService(MotoristaRepository repository, AuditoriaService auditoriaService) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public PageResponse<Resumo> listar(String q, Pageable pageable) {
        String busca = q == null ? "" : q.trim();
        return PageResponse.of(repository.buscar(busca, pageable), MotoristaMapper::paraResumo);
    }

    @Transactional(readOnly = true)
    public Detalhe buscar(Long id) {
        return MotoristaMapper.paraDetalhe(carregar(id));
    }

    /** "Minhas entregas": motorista vinculado ao usuario logado. 404 se nao for motorista de ninguem. */
    @Transactional(readOnly = true)
    public Detalhe buscarPorUsuarioLogado(Long usuarioId) {
        return MotoristaMapper.paraDetalhe(
                repository.findByUsuarioIdAndDeletedAtIsNull(usuarioId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException(
                                "Voce nao esta cadastrado como motorista.")));
    }

    @Transactional
    public Detalhe criar(SalvarRequest req) {
        garantirEmailLivre(req.email(), null);

        var novo = new Motorista();
        MotoristaMapper.aplicar(req, novo);
        // empresaId nao e atribuido aqui: o @TenantId faz o Hibernate gravar o
        // tenant da requisicao.
        novo = repository.save(novo);

        log.info("Motorista criado: id={} empresa={}", novo.getId(), TenantContext.get());
        return MotoristaMapper.paraDetalhe(novo);
    }

    @Transactional
    public Detalhe atualizar(Long id, SalvarRequest req) {
        Motorista existente = carregar(id);
        garantirEmailLivre(req.email(), id);

        MotoristaMapper.aplicar(req, existente);
        return MotoristaMapper.paraDetalhe(repository.save(existente));
    }

    @Transactional
    public void excluir(Long id) {
        Motorista alvo = carregar(id);
        alvo.setDeletedAt(LocalDateTime.now());
        alvo.setDeletedSeq(alvo.getId());
        repository.save(alvo);

        auditoriaService.registrar(TenantContext.getObrigatorio(), "EXCLUSAO", "motorista", id, null);
        log.info("Motorista excluido: id={} empresa={}", id, TenantContext.get());
    }

    private Motorista carregar(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motorista nao encontrado."));
    }

    private void garantirEmailLivre(String email, Long idAtual) {
        if (email == null || email.isBlank()) {
            return;
        }
        boolean duplicado = idAtual == null
                ? repository.existsByEmailAndDeletedAtIsNull(email.trim())
                : repository.existsByEmailAndDeletedAtIsNullAndIdNot(email.trim(), idAtual);

        if (duplicado) {
            throw new RegraDeNegocioException(
                    "Ja existe um motorista com o e-mail " + email.trim() + ".");
        }
    }
}
