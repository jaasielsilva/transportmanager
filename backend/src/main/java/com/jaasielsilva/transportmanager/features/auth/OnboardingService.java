package com.jaasielsilva.transportmanager.features.auth;

import com.jaasielsilva.transportmanager.common.AuditoriaService;
import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.RegraDeNegocioException;
import com.jaasielsilva.transportmanager.features.auth.AuthDtos.ConviteRequest;
import com.jaasielsilva.transportmanager.features.auth.AuthDtos.SignupRequest;
import com.jaasielsilva.transportmanager.features.billing.AssinaturaStatus;
import com.jaasielsilva.transportmanager.features.platform.Empresa;
import com.jaasielsilva.transportmanager.features.platform.EmpresaRepository;
import com.jaasielsilva.transportmanager.features.platform.QuotaService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Molde do kit — features/auth/OnboardingService.java
 *
 * Entrada de novos clientes: signup self-service e convite de usuario.
 */
@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private static final int DIAS_DE_TRIAL = 14;
    private static final Long PLANO_TRIAL_ID = 1L;   // seed do V1__init.sql

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;
    private final EmailService emailService;
    private final AuditoriaService auditoriaService;
    private final QuotaService quotaService;
    private final PasswordEncoder passwordEncoder;

    public OnboardingService(EmpresaRepository empresaRepository,
                             UsuarioRepository usuarioRepository,
                             AuthService authService,
                             EmailService emailService,
                             AuditoriaService auditoriaService,
                             QuotaService quotaService,
                             PasswordEncoder passwordEncoder) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.authService = authService;
        this.emailService = emailService;
        this.auditoriaService = auditoriaService;
        this.quotaService = quotaService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Cria empresa + primeiro TENANT_ADMIN numa UNICA transacao. Se qualquer
     * parte falhar, nada e gravado: empresa sem admin e um tenant orfao que
     * ninguem consegue acessar nem excluir pela aplicacao.
     *
     * Roda sem tenant — ele ainda nao existe.
     */
    @Transactional
    public void criarTenant(SignupRequest req) {
        TenantContext.semFiltroDeTenant(() -> {
            if (empresaRepository.findByDocumento(req.documento()).isPresent()) {
                throw new RegraDeNegocioException("Ja existe uma conta com este documento.");
            }
            if (usuarioRepository.existsByEmail(req.email())) {
                throw new RegraDeNegocioException("Este e-mail ja esta em uso.");
            }

            var empresa = new Empresa();
            empresa.setRazaoSocial(req.razaoSocial());
            empresa.setDocumento(req.documento());
            empresa.setPlanoId(PLANO_TRIAL_ID);
            empresa.setAssinaturaStatus(AssinaturaStatus.TRIALING);
            empresa.setTrialExpiraEm(LocalDate.now().plusDays(DIAS_DE_TRIAL));
            empresa = empresaRepository.save(empresa);

            var admin = new Usuario();
            admin.setEmpresaId(empresa.getId());
            admin.setNome(req.nomeAdmin());
            admin.setEmail(req.email());
            admin.setSenhaHash(passwordEncoder.encode(req.senha()));
            admin.setRoles(Set.of("TENANT_ADMIN"));
            usuarioRepository.save(admin);

            auditoriaService.registrar(empresa.getId(), "SIGNUP", "empresa", empresa.getId(), null);
            log.info("Novo tenant: empresa {} em trial ate {}", empresa.getId(), empresa.getTrialExpiraEm());

            emailService.enviarBoasVindas(admin, empresa);

            // TODO por projeto: popular dados de demonstracao aqui.
            // Trial que abre numa tela vazia nao converte — ver ARQUITETURA,
            // secao Ativacao.

            return null;
        });
    }

    /**
     * Convite: o usuario e criado SEM senha e inativo; quem define a senha e o
     * proprio convidado, pelo link. Assim nenhuma senha trafega por e-mail.
     */
    @Transactional
    public void convidar(ConviteRequest req, Long empresaId) {
        if (usuarioRepository.existsByEmail(req.email())) {
            throw new RegraDeNegocioException("Este e-mail ja esta em uso.");
        }

        // Quota do plano ANTES de criar: excedeu -> 409 com caminho de upgrade.
        // Convite e o momento em que o cliente esta querendo usar mais o
        // produto — a hora de oferecer o plano maior, nao de dar erro seco.
        quotaService.exigirCapacidade(empresaId, "MAX_USUARIOS",
                usuarioRepository.countByEmpresaIdAndDeletedAtIsNull(empresaId));

        var convidado = new Usuario();
        convidado.setEmpresaId(empresaId);
        convidado.setNome(req.nome());
        convidado.setEmail(req.email());
        convidado.setSenhaHash(null);
        convidado.setAtivo(false);
        convidado.setRoles(Set.of(req.role()));
        convidado = usuarioRepository.save(convidado);

        String token = authService.criarToken(convidado, TokenAcesso.Tipo.CONVITE, 7, ChronoUnit.DAYS);
        emailService.enviarConvite(convidado, token);

        auditoriaService.registrar(empresaId, "CONVITE_ENVIADO", "usuario", convidado.getId(), null);
    }
}
