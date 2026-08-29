package com.jaasielsilva.transportmanager.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jaasielsilva.transportmanager.common.AuditoriaService;
import com.jaasielsilva.transportmanager.config.security.JwtService;
import com.jaasielsilva.transportmanager.exception.RecursoNaoEncontradoException;
import com.jaasielsilva.transportmanager.exception.RegraDeNegocioException;
import com.jaasielsilva.transportmanager.features.auth.AuthDtos.AlterarSenhaRequest;
import com.jaasielsilva.transportmanager.features.billing.AssinaturaService;
import com.jaasielsilva.transportmanager.features.platform.EmpresaRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Molde do kit — teste de AuthService.alterarSenha.
 *
 * Cobre a regra que sustenta o endpoint: so troca quem prova saber a senha
 * atual, e trocar derruba as sessoes abertas.
 *
 * O caminho "usuario trocando a senha de outro" nao aparece aqui de proposito —
 * ele nao existe: o id vem do token, nunca do corpo (ver
 * UsuarioController.alterarSenha).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceAlterarSenhaTest {

    private static final Long USUARIO = 42L;
    private static final Long EMPRESA = 7L;
    private static final String HASH_ATUAL = "$2b$10$hash-da-senha-atual";

    @Mock UsuarioRepository usuarioRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock TokenAcessoRepository tokenAcessoRepository;
    @Mock EmpresaRepository empresaRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;
    @Mock AuditoriaService auditoriaService;
    @Mock AssinaturaService assinaturaService;

    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(usuarioRepository, refreshTokenRepository,
                tokenAcessoRepository, empresaRepository, jwtService, passwordEncoder,
                emailService, auditoriaService, assinaturaService);
    }

    private Usuario usuario(String senhaHash) {
        var u = new Usuario();
        u.setId(USUARIO);
        u.setEmpresaId(EMPRESA);
        u.setNome("Fulano");
        u.setEmail("fulano@teste.com");
        u.setSenhaHash(senhaHash);
        u.setAtivo(true);
        return u;
    }

    @Test
    @DisplayName("troca a senha, zera o bloqueio e derruba as sessoes abertas")
    void trocaComSucesso() {
        Usuario u = usuario(HASH_ATUAL);
        u.setTentativasFalhas((short) 3);
        when(usuarioRepository.findById(USUARIO)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("atual123", HASH_ATUAL)).thenReturn(true);
        when(passwordEncoder.matches("nova12345", HASH_ATUAL)).thenReturn(false);
        when(passwordEncoder.encode("nova12345")).thenReturn("$2b$10$hash-novo");

        service.alterarSenha(USUARIO, new AlterarSenhaRequest("atual123", "nova12345"));

        assertThat(u.getSenhaHash()).isEqualTo("$2b$10$hash-novo");
        assertThat(u.getTentativasFalhas()).isZero();
        assertThat(u.getBloqueadoAte()).isNull();
        verify(usuarioRepository).save(u);
        // O ponto do teste: sem isto, uma sessao roubada continua valendo
        // depois da troca — e a troca costuma ser a reacao ao roubo.
        verify(refreshTokenRepository).revogarTodosDoUsuario(USUARIO);
        verify(auditoriaService).registrar(eq(EMPRESA), eq("SENHA_ALTERADA"),
                eq("usuario"), eq(USUARIO), any());
    }

    @Test
    @DisplayName("senha atual errada nao altera nada")
    void senhaAtualErrada() {
        Usuario u = usuario(HASH_ATUAL);
        when(usuarioRepository.findById(USUARIO)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("chutei", HASH_ATUAL)).thenReturn(false);

        assertThatThrownBy(() -> service.alterarSenha(USUARIO,
                new AlterarSenhaRequest("chutei", "nova12345")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Senha atual incorreta");

        assertThat(u.getSenhaHash()).isEqualTo(HASH_ATUAL);
        verify(usuarioRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revogarTodosDoUsuario(any());
    }

    @Test
    @DisplayName("convite nunca aceito (sem senha) nao passa pela troca")
    void semSenhaDefinida() {
        when(usuarioRepository.findById(USUARIO)).thenReturn(Optional.of(usuario(null)));

        assertThatThrownBy(() -> service.alterarSenha(USUARIO,
                new AlterarSenhaRequest("qualquer", "nova12345")))
                .isInstanceOf(RegraDeNegocioException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("nova senha igual a atual e recusada")
    void novaIgualAAtual() {
        Usuario u = usuario(HASH_ATUAL);
        when(usuarioRepository.findById(USUARIO)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("atual123", HASH_ATUAL)).thenReturn(true);

        assertThatThrownBy(() -> service.alterarSenha(USUARIO,
                new AlterarSenhaRequest("atual123", "atual123")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("diferente da atual");

        verify(usuarioRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revogarTodosDoUsuario(any());
    }

    @Test
    @DisplayName("usuario inexistente vira 404, nao 500")
    void usuarioInexistente() {
        when(usuarioRepository.findById(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.alterarSenha(USUARIO,
                new AlterarSenhaRequest("atual123", "nova12345")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
