package com.jaasielsilva.transportmanager.features.cep.controller;

import com.jaasielsilva.transportmanager.common.ApiResponse;
import com.jaasielsilva.transportmanager.features.cep.dto.CepDtos.CepDados;
import com.jaasielsilva.transportmanager.features.cep.service.CepService;
import com.jaasielsilva.transportmanager.features.platform.Modulo;
import com.jaasielsilva.transportmanager.features.platform.RequiresModule;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autofill do form de carga: CEP -> endereco/cidade/UF. So traduz HTTP, sem
 * regra de negocio. Mesmo gate comercial do modulo carga (@RequiresModule +
 * @PreAuthorize): quem acessa as cargas usa o autofill. CEP inexistente devolve
 * data null (nao 404) — o form mostra "CEP nao encontrado" e deixa o usuario
 * digitar na mao.
 */
@RestController
@RequestMapping("/api/v1/ceps")
@RequiresModule(Modulo.CADASTROS)
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'USER')")
@Validated
public class CepController {

    private final CepService service;

    public CepController(CepService service) {
        this.service = service;
    }

    @GetMapping("/{cep}")
    public ApiResponse<CepDados> buscar(
            @PathVariable
            @Pattern(regexp = "\\d{8}", message = "CEP invalido: informe 8 digitos.")
            String cep) {
        return ApiResponse.ok(service.buscar(cep));
    }
}
