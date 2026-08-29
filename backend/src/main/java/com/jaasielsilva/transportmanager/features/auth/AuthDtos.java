package com.jaasielsilva.transportmanager.features.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Molde do kit — features/auth/AuthDtos.java
 *
 * DTOs de request e response da autenticacao. Sempre record, sempre validados,
 * nunca a Entity na API.
 */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String senha) {}

    /**
     * O refresh token NAO aparece aqui: ele viaja em cookie httpOnly, fora do
     * alcance do JavaScript. O access token fica so em memoria no Angular.
     * Guardar qualquer um dos dois em localStorage transforma um XSS em conta
     * roubada.
     */
    public record LoginResponse(
            String accessToken,
            long expiraEmSegundos,
            UsuarioLogado usuario) {}

    /**
     * O que o front precisa para desenhar a aplicacao inteira: quem e, de qual
     * empresa, o que pode (roles), o que o plano habilita (modulos) e como
     * esta a cobranca (assinaturaStatus + nivelAcesso).
     *
     * Os modulos vao aqui e nao so na claim do token porque o front nao deve
     * decodificar JWT para montar menu: token e credencial, nao fonte de dados
     * de tela. Quem bloqueia continua sendo o backend — isto e para ESCONDER.
     */
    public record UsuarioLogado(
            Long id,
            String nome,
            String email,
            Long empresaId,
            String empresa,
            Set<String> roles,
            Set<String> modulos,
            String assinaturaStatus,
            String nivelAcesso) {}

    public record SignupRequest(
            @NotBlank @Size(max = 150) String razaoSocial,
            @NotBlank @Size(max = 20) String documento,
            @NotBlank @Size(max = 120) String nomeAdmin,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72) String senha) {}

    public record EsqueciSenhaRequest(
            @NotBlank @Email String email) {}

    public record RedefinirSenhaRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String novaSenha) {}

    /**
     * Troca de senha de quem ja esta logado — nao confundir com
     * RedefinirSenhaRequest, que vale por um token de e-mail e serve para quem
     * NAO consegue entrar.
     *
     * A senha atual e obrigatoria: ela e a prova de identidade aqui. Sem ela,
     * uma sessao aberta num computador emprestado (ou um XSS) troca a senha e
     * toma a conta sem nunca ter sabido a antiga.
     */
    public record AlterarSenhaRequest(
            @NotBlank String senhaAtual,
            @NotBlank @Size(min = 8, max = 72) String novaSenha) {}

    public record ConviteRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank String role) {}

    public record AceitarConviteRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String senha) {}
}
