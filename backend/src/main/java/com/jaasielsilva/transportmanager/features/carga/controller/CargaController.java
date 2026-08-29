package com.jaasielsilva.transportmanager.features.carga.controller;

import com.jaasielsilva.transportmanager.common.ApiResponse;
import com.jaasielsilva.transportmanager.common.PageResponse;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.Detalhe;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.Resumo;
import com.jaasielsilva.transportmanager.features.carga.dto.CargaDtos.SalvarRequest;
import com.jaasielsilva.transportmanager.features.carga.service.CargaService;
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
import org.springframework.web.bind.annotation.*;

/**
 * Molde do kit — features/carga/controller/CargaController.java
 *
 * CONTROLLER DE REFERENCIA: so traduz HTTP. Sem regra de negocio, sem
 * try/catch (o GlobalExceptionHandler cuida), sem Entity na resposta.
 *
 * As duas anotacoes de cima sao o modelo comercial em vigor:
 *   @RequiresModule — quem nao tem o modulo no plano recebe 403
 *   @PreAuthorize   — quem nao tem a role recebe 403
 * O interceptor de assinatura roda antes das duas: cliente em atraso recebe
 * 402 e vai para a tela de regularizacao, nao para a de upgrade.
 */
@RestController
@RequestMapping("/api/v1/cargas")
@RequiresModule(Modulo.CADASTROS)
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'USER')")
public class CargaController {

    /** Teto de paginacao. Vale para toda listagem do sistema. */
    private static final int SIZE_MAXIMO = 100;

    private final CargaService service;

    public CargaController(CargaService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<Resumo>> listar(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ApiResponse.ok(service.listar(q, limitar(pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Detalhe> buscar(@PathVariable Long id) {
        return ApiResponse.ok(service.buscar(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Detalhe>> criar(@Valid @RequestBody SalvarRequest req) {
        Detalhe criado = service.criar(req);
        return ResponseEntity
                .created(URI.create("/api/v1/cargas/" + criado.id()))
                .body(ApiResponse.ok(criado, "Carga cadastrado."));
    }

    @PutMapping("/{id}")
    public ApiResponse<Detalhe> atualizar(@PathVariable Long id,
                                          @Valid @RequestBody SalvarRequest req) {
        return ApiResponse.ok(service.atualizar(id, req), "Alteracoes salvas.");
    }

    /** Exclusao e soft delete e so o admin do tenant faz. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ApiResponse.ok(null, "Carga excluido.");
    }

    /**
     * O size vem da query string, ou seja, do cliente. Sem teto, um size=100000
     * carrega a tabela inteira na memoria da aplicacao — e nao precisa de
     * ma-fe, basta um script de integracao mal calibrado.
     */
    private Pageable limitar(Pageable pageable) {
        return pageable.getPageSize() <= SIZE_MAXIMO
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), SIZE_MAXIMO, pageable.getSort());
    }
}
