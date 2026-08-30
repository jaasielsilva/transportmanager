package com.jaasielsilva.transportmanager.features.motorista.controller;

import com.jaasielsilva.transportmanager.common.ApiResponse;
import com.jaasielsilva.transportmanager.common.PageResponse;
import com.jaasielsilva.transportmanager.features.motorista.dto.MotoristaDtos.Detalhe;
import com.jaasielsilva.transportmanager.features.motorista.dto.MotoristaDtos.Resumo;
import com.jaasielsilva.transportmanager.features.motorista.dto.MotoristaDtos.SalvarRequest;
import com.jaasielsilva.transportmanager.features.motorista.service.MotoristaService;
import com.jaasielsilva.transportmanager.features.platform.Modulo;
import com.jaasielsilva.transportmanager.features.platform.RequiresModule;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Molde do kit — features/carga/controller/CargaController.java. So traduz
 * HTTP: sem regra de negocio, sem try/catch, sem Entity na resposta.
 */
@RestController
@RequestMapping("/api/v1/motoristas")
@RequiresModule(Modulo.CADASTROS)
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'USER')")
public class MotoristaController {

    private static final int SIZE_MAXIMO = 100;

    private final MotoristaService service;

    public MotoristaController(MotoristaService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<Resumo>> listar(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ApiResponse.ok(service.listar(q, limitar(pageable)));
    }

    /**
     * Motorista vinculado ao usuario logado. Usado pela tela "Minhas entregas".
     * Vem ANTES de /{id} nao por causa de rota (nao colide, "me" nao e Long),
     * mas para deixar explicito que e um endpoint proprio, nao um detalhe.
     */
    @GetMapping("/me")
    public ApiResponse<Detalhe> meuCadastro(@AuthenticationPrincipal Long usuarioId) {
        return ApiResponse.ok(service.buscarPorUsuarioLogado(usuarioId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Detalhe> buscar(@PathVariable Long id) {
        return ApiResponse.ok(service.buscar(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Detalhe>> criar(@Valid @RequestBody SalvarRequest req) {
        Detalhe criado = service.criar(req);
        return ResponseEntity
                .created(URI.create("/api/v1/motoristas/" + criado.id()))
                .body(ApiResponse.ok(criado, "Motorista cadastrado."));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<Detalhe> atualizar(@PathVariable Long id, @Valid @RequestBody SalvarRequest req) {
        return ApiResponse.ok(service.atualizar(id, req), "Alteracoes salvas.");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ApiResponse.ok(null, "Motorista excluido.");
    }

    private Pageable limitar(Pageable pageable) {
        return pageable.getPageSize() <= SIZE_MAXIMO
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), SIZE_MAXIMO, pageable.getSort());
    }
}
