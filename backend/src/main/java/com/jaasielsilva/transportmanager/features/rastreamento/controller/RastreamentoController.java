package com.jaasielsilva.transportmanager.features.rastreamento.controller;

import com.jaasielsilva.transportmanager.common.ApiResponse;
import com.jaasielsilva.transportmanager.features.platform.Modulo;
import com.jaasielsilva.transportmanager.features.platform.RequiresModule;
import com.jaasielsilva.transportmanager.features.rastreamento.dto.RastreamentoDtos.PosicaoAtual;
import com.jaasielsilva.transportmanager.features.rastreamento.dto.RastreamentoDtos.RegistrarPosicaoRequest;
import com.jaasielsilva.transportmanager.features.rastreamento.service.RastreamentoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Rastreamento GPS de uma carga em transito. Primeiro uso real do modulo
 * OPERACAO (ate aqui so existia no enum, sem feature nenhuma atras dele).
 */
@RestController
@RequestMapping("/api/v1/cargas/{id}/posicoes")
@RequiresModule(Modulo.OPERACAO)
public class RastreamentoController {

    private final RastreamentoService service;

    public RastreamentoController(RastreamentoService service) {
        this.service = service;
    }

    /** O motorista envia a propria posicao. A posse (motorista == usuario logado) e checada no Service. */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> registrar(@PathVariable Long id,
                                       @Valid @RequestBody RegistrarPosicaoRequest req,
                                       @AuthenticationPrincipal Long usuarioId) {
        service.registrarPosicao(id, usuarioId, req);
        return ApiResponse.ok(null, "Posicao registrada.");
    }

    /** Ultima posicao conhecida — o mapa de quem acompanha consulta isto a cada ~8s. */
    @GetMapping("/atual")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'USER')")
    public ApiResponse<PosicaoAtual> atual(@PathVariable Long id) {
        return ApiResponse.ok(service.posicaoAtual(id));
    }

    /** Trilha historica — usada para desenhar o caminho ja percorrido (opcional na v1). */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'USER')")
    public ApiResponse<List<PosicaoAtual>> trilha(@PathVariable Long id) {
        return ApiResponse.ok(service.trilha(id));
    }
}
