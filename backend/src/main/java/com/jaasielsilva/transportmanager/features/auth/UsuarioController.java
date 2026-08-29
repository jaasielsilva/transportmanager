package com.jaasielsilva.transportmanager.features.auth;

import com.jaasielsilva.transportmanager.common.ApiResponse;
import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.features.auth.AuthDtos.AlterarSenhaRequest;
import com.jaasielsilva.transportmanager.features.auth.AuthDtos.ConviteRequest;
import com.jaasielsilva.transportmanager.features.auth.AuthDtos.UsuarioLogado;
import com.jaasielsilva.transportmanager.features.billing.AssinaturaService;
import com.jaasielsilva.transportmanager.features.platform.EmpresaRepository;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Molde do kit — features/auth/UsuarioController.java
 *
 * Contexto do usuario logado e gestao de usuarios do tenant.
 *
 * O /me e isento do interceptor de assinatura de proposito: mesmo suspenso, o
 * front precisa saber quem esta logado e qual o nivel de acesso para desenhar
 * a tela de regularizacao em vez de uma tela quebrada.
 */
@RestController
@RequestMapping("/api/v1")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final OnboardingService onboardingService;
    private final AssinaturaService assinaturaService;
    private final AuthService authService;

    public UsuarioController(UsuarioRepository usuarioRepository,
                             EmpresaRepository empresaRepository,
                             OnboardingService onboardingService,
                             AssinaturaService assinaturaService,
                             AuthService authService) {
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.onboardingService = onboardingService;
        this.assinaturaService = assinaturaService;
        this.authService = authService;
    }

    /**
     * Quem sou eu. O front chama isto ao carregar para montar menu, permissoes
     * e banner de cobranca — em vez de confiar no que guardou da ultima sessao.
     *
     * O principal e o userId, colocado no contexto pelo JwtAuthFilter.
     */
    @GetMapping("/me")
    public ApiResponse<UsuarioLogado> eu(@AuthenticationPrincipal Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado."));

        var empresa = TenantContext.semFiltroDeTenant(
                () -> empresaRepository.findById(usuario.getEmpresaId()).orElseThrow());

        // Modulos lidos do banco, e nao da claim do token: o /me e o que o
        // front chama depois de trocar de plano para a tela refletir a compra
        // sem esperar o proximo refresh.
        var modulos = TenantContext.semFiltroDeTenant(
                () -> empresaRepository.modulosDaEmpresa(empresa.getId()));

        return ApiResponse.ok(new UsuarioLogado(
                usuario.getId(), usuario.getNome(), usuario.getEmail(),
                empresa.getId(), empresa.getRazaoSocial(), usuario.getRoles(),
                Set.copyOf(modulos),
                empresa.getAssinaturaStatus().name(),
                assinaturaService.nivelDeAcessoDe(empresa.getId()).name()));
    }

    /**
     * Troca da propria senha. Mora aqui, e nao no AuthController, porque
     * /api/v1/auth/** e liberado sem token no SecurityConfig — este endpoint
     * PRECISA de sessao: o principal e justamente a prova de quem esta
     * trocando. Sem role exigida de proposito: todo mundo troca a propria
     * senha, PLATFORM_ADMIN incluido.
     *
     * Isento do interceptor de assinatura pelo prefixo /me (ver javadoc da
     * classe): prender a troca de senha atras da fatura em dia seria prender a
     * reacao a um vazamento.
     */
    @PostMapping("/me/senha")
    public ApiResponse<Void> alterarSenha(@Valid @RequestBody AlterarSenhaRequest req,
            @AuthenticationPrincipal Long usuarioId) {
        authService.alterarSenha(usuarioId, req);
        return ApiResponse.ok(null, "Senha alterada. Entre novamente com a nova senha.");
    }

    /**
     * Convite de novo usuario. So TENANT_ADMIN — quem entra na empresa e
     * decisao de quem responde por ela.
     *
     * O empresaId vem do TenantContext (ou seja, do token), nunca do corpo:
     * aceitar do corpo permitiria convidar gente para a empresa dos outros.
     */
    @PostMapping("/usuarios/convite")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<Void> convidar(@Valid @RequestBody ConviteRequest req) {
        onboardingService.convidar(req, TenantContext.getObrigatorio());
        return ApiResponse.ok(null, "Convite enviado para " + req.email() + ".");
    }
}
