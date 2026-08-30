package com.jaasielsilva.transportmanager.features.rastreamento.service;

import com.jaasielsilva.transportmanager.exception.AcessoNegadoException;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.exception.RegraDeNegocioException;
import com.jaasielsilva.transportmanager.features.carga.entity.Carga;
import com.jaasielsilva.transportmanager.features.carga.repository.CargaRepository;
import com.jaasielsilva.transportmanager.features.motorista.entity.Motorista;
import com.jaasielsilva.transportmanager.features.motorista.repository.MotoristaRepository;
import com.jaasielsilva.transportmanager.features.rastreamento.dto.RastreamentoDtos.PosicaoAtual;
import com.jaasielsilva.transportmanager.features.rastreamento.dto.RastreamentoDtos.RegistrarPosicaoRequest;
import com.jaasielsilva.transportmanager.features.rastreamento.entity.PosicaoGps;
import com.jaasielsilva.transportmanager.features.rastreamento.repository.PosicaoGpsRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regra de negocio do rastreamento GPS. Primeira feature "operacional" de
 * verdade do projeto: nao e CRUD, e um fluxo com posse de dado que o
 * @PreAuthorize (que so ve role) nao cobre.
 */
@Service
public class RastreamentoService {

    private static final Logger log = LoggerFactory.getLogger(RastreamentoService.class);

    private static final String STATUS_EM_TRANSITO = "EM_TRANSITO";

    private final CargaRepository cargaRepository;
    private final MotoristaRepository motoristaRepository;
    private final PosicaoGpsRepository posicaoGpsRepository;

    public RastreamentoService(CargaRepository cargaRepository,
                                MotoristaRepository motoristaRepository,
                                PosicaoGpsRepository posicaoGpsRepository) {
        this.cargaRepository = cargaRepository;
        this.motoristaRepository = motoristaRepository;
        this.posicaoGpsRepository = posicaoGpsRepository;
    }

    /**
     * Registra a posicao do motorista para a carga.
     * <ol>
     *   <li>Carga precisa existir (tenant, via @TenantId) e estar EM_TRANSITO — senao 409;</li>
     *   <li>Carga precisa ter motorista vinculado, e esse motorista precisa
     *       pertencer ao usuario autenticado — senao 403.</li>
     * </ol>
     */
    @Transactional
    public void registrarPosicao(Long cargaId, Long usuarioAutenticado, RegistrarPosicaoRequest req) {
        Carga carga = carregarCarga(cargaId);

        if (!STATUS_EM_TRANSITO.equals(carga.getStatus())) {
            throw new RegraDeNegocioException(
                    "So e possivel registrar posicao com a carga em transito.");
        }

        Motorista motorista = carregarMotoristaDaCarga(carga);
        if (motorista.getUsuarioId() == null || !motorista.getUsuarioId().equals(usuarioAutenticado)) {
            throw new AcessoNegadoException("Voce nao e o motorista responsavel por esta carga.");
        }

        var posicao = new PosicaoGps();
        posicao.setCargaId(carga.getId());
        posicao.setMotoristaId(motorista.getId());
        posicao.setLatitude(req.latitude());
        posicao.setLongitude(req.longitude());
        posicao.setRegistradoEm(LocalDateTime.now());
        posicaoGpsRepository.save(posicao);

        log.info("Posicao GPS registrada: carga={} motorista={}", carga.getId(), motorista.getId());
    }

    @Transactional(readOnly = true)
    public PosicaoAtual posicaoAtual(Long cargaId) {
        carregarCarga(cargaId); // 404 se a carga nao existe ou e de outro tenant
        return posicaoGpsRepository.findTopByCargaIdOrderByRegistradoEmDesc(cargaId)
                .map(p -> new PosicaoAtual(p.getLatitude(), p.getLongitude(), p.getRegistradoEm()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhuma posicao registrada para esta carga ainda."));
    }

    @Transactional(readOnly = true)
    public List<PosicaoAtual> trilha(Long cargaId) {
        carregarCarga(cargaId);
        return posicaoGpsRepository.findByCargaIdOrderByRegistradoEmAsc(cargaId).stream()
                .map(p -> new PosicaoAtual(p.getLatitude(), p.getLongitude(), p.getRegistradoEm()))
                .toList();
    }

    /** 404 tambem para carga de outro tenant — o @TenantId ja filtra a busca. */
    private Carga carregarCarga(Long cargaId) {
        return cargaRepository.findByIdAndDeletedAtIsNull(cargaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Carga nao encontrada."));
    }

    private Motorista carregarMotoristaDaCarga(Carga carga) {
        if (carga.getMotoristaId() == null) {
            throw new AcessoNegadoException("Esta carga nao tem motorista vinculado.");
        }
        return motoristaRepository.findByIdAndDeletedAtIsNull(carga.getMotoristaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motorista da carga nao encontrado."));
    }
}
