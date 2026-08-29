package com.jaasielsilva.transportmanager.features.auth;

import com.jaasielsilva.transportmanager.common.AuditoriaService;
import com.jaasielsilva.transportmanager.config.security.JwtService;
import com.jaasielsilva.transportmanager.config.tenant.TenantContext;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.exception.RegraDeNegocioException;
import com.jaasielsilva.transportmanager.features.auth.AuthDtos.*;
import com.jaasielsilva.transportmanager.features.billing.AssinaturaService;
import com.jaasielsilva.transportmanager.features.platform.Empresa;
import com.jaasielsilva.transportmanager.features.platform.EmpresaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Molde do kit — features/auth/AuthService.java
 *
 * Login, refresh rotacionado, logout e recuperacao de senha.
 *
 * Os detalhes aqui sao quase todos de seguranca, e cada um evita um ataque
 * concreto — estao comentados no ponto em que acontecem.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Apos 5 falhas seguidas, 15 min de bloqueio. Trava forca bruta sem irritar quem so errou a senha. */
    private static final short MAX_TENTATIVAS = 5;
    private static final int MINUTOS_BLOQUEIO = 15;

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenAcessoRepository tokenAcessoRepository;
    private final EmpresaRepository empresaRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditoriaService auditoriaService;
    private final AssinaturaService assinaturaService;

    public AuthService(UsuarioRepository usuarioRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       TokenAcessoRepository tokenAcessoRepository,
                       EmpresaRepository empresaRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       AuditoriaService auditoriaService,
                       AssinaturaService assinaturaService) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenAcessoRepository = tokenAcessoRepository;
        this.empresaRepository = empresaRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditoriaService = auditoriaService;
        this.assinaturaService = assinaturaService;
    }

    // =================================================================
    // Login
    // =================================================================

    /** @return o par (resposta, refreshTokenBruto). O bruto vai para o cookie httpOnly. */
    @Transactional
    public ResultadoLogin login(LoginRequest req, String ip) {
        Usuario usuario = usuarioRepository.findByEmailAndDeletedAtIsNull(req.email())
                .orElseThrow(AuthService::credenciaisInvalidas);

        if (usuario.isBloqueado()) {
            throw new RegraDeNegocioException(
                    "Muitas tentativas. Tente novamente em alguns minutos.");
        }
        if (!usuario.isAtivo() || usuario.getSenhaHash() == null) {
            throw credenciaisInvalidas();
        }

        if (!passwordEncoder.matches(req.senha(), usuario.getSenhaHash())) {
            registrarFalha(usuario, ip);
            throw credenciaisInvalidas();
        }

        usuario.setTentativasFalhas((short) 0);
        usuario.setBloqueadoAte(null);
        usuario.setUltimoLoginEm(LocalDateTime.now());
        usuarioRepository.save(usuario);

        auditoriaService.registrar(usuario.getEmpresaId(), "LOGIN", "usuario", usuario.getId(), ip);
        return emitirTokens(usuario, ip);
    }

    /**
     * Mensagem unica para e-mail inexistente e senha errada. Diferenciar as
     * duas entrega ao atacante a lista de quem tem conta no sistema.
     */
    private static RegraDeNegocioException credenciaisInvalidas() {
        return new RegraDeNegocioException("E-mail ou senha invalidos.");
    }

    private void registrarFalha(Usuario usuario, String ip) {
        short tentativas = (short) (usuario.getTentativasFalhas() + 1);
        usuario.setTentativasFalhas(tentativas);
        if (tentativas >= MAX_TENTATIVAS) {
            usuario.setBloqueadoAte(LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEIO));
            auditoriaService.registrar(usuario.getEmpresaId(), "LOGIN_BLOQUEADO",
                    "usuario", usuario.getId(), ip);
            log.warn("Login bloqueado por tentativas: usuario {}", usuario.getId());
        }
        usuarioRepository.save(usuario);
    }

    // =================================================================
    // Refresh rotacionado
    // =================================================================

    /**
     * Cada refresh invalida o token usado e emite outro. Se um token ja
     * rotacionado voltar, alguem copiou o cookie: derrubamos a cadeia inteira
     * daquele usuario. Detectar reuso e o unico jeito de perceber um roubo de
     * refresh token.
     */
    @Transactional
    public ResultadoLogin refresh(String refreshTokenBruto, String ip) {
        if (refreshTokenBruto == null || refreshTokenBruto.isBlank()) {
            throw new RegraDeNegocioException("Sessao expirada. Faca login novamente.");
        }

        String hash = jwtService.hash(refreshTokenBruto);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new RegraDeNegocioException("Sessao expirada. Faca login novamente."));

        if (!token.isValido()) {
            if (token.getSubstituidoPor() != null) {
                log.warn("Reuso de refresh token detectado: usuario {}", token.getUsuarioId());
                refreshTokenRepository.revogarTodosDoUsuario(token.getUsuarioId());
                auditoriaService.registrar(null, "REFRESH_REUSO_DETECTADO",
                        "usuario", token.getUsuarioId(), ip);
            }
            throw new RegraDeNegocioException("Sessao expirada. Faca login novamente.");
        }

        Usuario usuario = usuarioRepository.findById(token.getUsuarioId())
                .orElseThrow(() -> new RegraDeNegocioException("Sessao invalida."));

        ResultadoLogin novo = emitirTokens(usuario, ip);

        token.setRevogadoEm(LocalDateTime.now());
        token.setSubstituidoPor(novo.refreshTokenId());
        refreshTokenRepository.save(token);

        return novo;
    }

    @Transactional
    public void logout(String refreshTokenBruto) {
        if (refreshTokenBruto == null || refreshTokenBruto.isBlank()) {
            return;                       // logout e idempotente de proposito
        }
        refreshTokenRepository.findByTokenHash(jwtService.hash(refreshTokenBruto))
                .ifPresent(t -> {
                    t.setRevogadoEm(LocalDateTime.now());
                    refreshTokenRepository.save(t);
                });
    }

    private ResultadoLogin emitirTokens(Usuario usuario, String ip) {
        Empresa empresa = TenantContext.semFiltroDeTenant(
                () -> empresaRepository.findById(usuario.getEmpresaId()).orElseThrow());

        // Modulos do plano viram claim no token. Mudou de plano, vale no
        // proximo refresh — no maximo 15 min de defasagem.
        List<String> modulos = TenantContext.semFiltroDeTenant(
                () -> empresaRepository.modulosDaEmpresa(usuario.getEmpresaId()));

        String accessToken = jwtService.gerarAccessToken(
                usuario.getId(), usuario.getEmpresaId(), usuario.getRoles(), modulos);

        String bruto = jwtService.gerarRefreshTokenBruto();
        RefreshToken refresh = new RefreshToken();
        refresh.setUsuarioId(usuario.getId());
        refresh.setTokenHash(jwtService.hash(bruto));
        refresh.setExpiraEm(LocalDateTime.ofInstant(
                jwtService.expiracaoDoRefresh(), java.time.ZoneId.systemDefault()));
        refresh.setIp(ip);
        refresh = refreshTokenRepository.save(refresh);

        var dados = new UsuarioLogado(
                usuario.getId(), usuario.getNome(), usuario.getEmail(),
                empresa.getId(), empresa.getRazaoSocial(), usuario.getRoles(),
                Set.copyOf(modulos),
                empresa.getAssinaturaStatus().name(),
                assinaturaService.nivelDeAcessoDe(empresa.getId()).name());

        return new ResultadoLogin(
                new LoginResponse(accessToken, jwtService.segundosDoAccessToken(), dados),
                bruto, refresh.getId());
    }

    // =================================================================
    // Recuperacao de senha e convite
    // =================================================================

    /**
     * Resposta SEMPRE igual, exista ou nao o e-mail. Responder diferente
     * transforma este endpoint num verificador de quem tem conta.
     */
    @Transactional
    public void esqueciSenha(EsqueciSenhaRequest req) {
        Optional<Usuario> talvez = usuarioRepository.findByEmailAndDeletedAtIsNull(req.email());
        if (talvez.isEmpty()) {
            log.info("Reset de senha solicitado para e-mail inexistente");
            return;
        }
        Usuario usuario = talvez.get();
        String bruto = criarToken(usuario, TokenAcesso.Tipo.RESET_SENHA, 30, java.time.temporal.ChronoUnit.MINUTES);
        emailService.enviarResetDeSenha(usuario, bruto);
    }

    @Transactional
    public void redefinirSenha(RedefinirSenhaRequest req) {
        TokenAcesso token = consumirToken(req.token(), TokenAcesso.Tipo.RESET_SENHA);
        Usuario usuario = usuarioRepository.findById(token.getUsuarioId()).orElseThrow();

        usuario.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
        usuario.setTentativasFalhas((short) 0);
        usuario.setBloqueadoAte(null);
        usuarioRepository.save(usuario);

        // Trocou a senha: toda sessao aberta cai. Se a troca foi por suspeita
        // de invasao, deixar as sessoes do atacante vivas anula a acao.
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId());
        auditoriaService.registrar(usuario.getEmpresaId(), "SENHA_REDEFINIDA",
                "usuario", usuario.getId(), null);
    }

    /**
     * Troca de senha de quem esta logado. Diferente do redefinirSenha, aqui nao
     * ha token de e-mail: a prova de identidade e saber a senha atual, e por
     * isso ela e conferida ANTES de qualquer escrita.
     *
     * O usuarioId vem do token (@AuthenticationPrincipal), nunca do corpo da
     * requisicao — aceitar do corpo deixaria qualquer um trocar a senha dos
     * outros.
     */
    @Transactional
    public void alterarSenha(Long usuarioId, AlterarSenhaRequest req) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado."));

        // senhaHash nulo = convite nunca aceito; nao ha senha atual para conferir.
        if (usuario.getSenhaHash() == null
                || !passwordEncoder.matches(req.senhaAtual(), usuario.getSenhaHash())) {
            auditoriaService.registrar(usuario.getEmpresaId(), "SENHA_ALTERADA_FALHA",
                    "usuario", usuario.getId(), null);
            throw new RegraDeNegocioException("Senha atual incorreta.");
        }

        // Sem esta checagem o usuario "troca" a senha pela mesma e sai achando
        // que rotacionou a credencial — perigoso justamente depois de um
        // vazamento, que e quando a troca mais importa.
        if (passwordEncoder.matches(req.novaSenha(), usuario.getSenhaHash())) {
            throw new RegraDeNegocioException("A nova senha deve ser diferente da atual.");
        }

        usuario.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
        usuario.setTentativasFalhas((short) 0);
        usuario.setBloqueadoAte(null);
        usuarioRepository.save(usuario);

        // Mesmo motivo do redefinirSenha: trocou a senha, toda sessao aberta
        // cai — inclusive a de quem trocou.
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId());
        auditoriaService.registrar(usuario.getEmpresaId(), "SENHA_ALTERADA",
                "usuario", usuario.getId(), null);
    }

    @Transactional
    public void aceitarConvite(AceitarConviteRequest req) {
        TokenAcesso token = consumirToken(req.token(), TokenAcesso.Tipo.CONVITE);
        Usuario usuario = usuarioRepository.findById(token.getUsuarioId()).orElseThrow();

        usuario.setSenhaHash(passwordEncoder.encode(req.senha()));
        usuario.setAtivo(true);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(usuario.getEmpresaId(), "CONVITE_ACEITO",
                "usuario", usuario.getId(), null);
    }

    /** Cria o token, guarda o hash e devolve o valor bruto (unica vez que ele existe em claro). */
    public String criarToken(Usuario usuario, TokenAcesso.Tipo tipo,
                             long quantidade, java.time.temporal.ChronoUnit unidade) {
        String bruto = jwtService.gerarRefreshTokenBruto();
        var token = new TokenAcesso();
        token.setUsuarioId(usuario.getId());
        token.setTipo(tipo);
        token.setTokenHash(jwtService.hash(bruto));
        token.setExpiraEm(LocalDateTime.now().plus(quantidade, unidade));
        tokenAcessoRepository.save(token);
        return bruto;
    }

    private TokenAcesso consumirToken(String bruto, TokenAcesso.Tipo tipo) {
        TokenAcesso token = tokenAcessoRepository.findByTokenHash(jwtService.hash(bruto))
                .orElseThrow(() -> new RegraDeNegocioException("Link invalido ou ja utilizado."));

        if (token.getTipo() != tipo || !token.isValido()) {
            throw new RegraDeNegocioException("Link invalido ou expirado. Solicite outro.");
        }
        token.setUsadoEm(LocalDateTime.now());     // uso unico
        tokenAcessoRepository.save(token);
        return token;
    }

    /** Resposta do login/refresh: o que vai no corpo e o que vai no cookie. */
    public record ResultadoLogin(LoginResponse resposta, String refreshTokenBruto, Long refreshTokenId) {}
}
