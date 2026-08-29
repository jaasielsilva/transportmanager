package com.jaasielsilva.transportmanager.features.auth;

import com.jaasielsilva.transportmanager.features.platform.Empresa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Molde do kit — features/auth/EmailServiceImpl.java
 *
 * Implementacao SMTP. Trocar de provedor = trocar esta classe, sem tocar em
 * nenhuma regra de negocio.
 *
 * O @Async fica nos metodos PUBLICOS, que sao os chamados de fora. Estava num
 * metodo auxiliar privado antes, e como o Spring aplica @Async por proxy, uma
 * chamada interna nao passa por ele: o envio acontecia de forma sincrona e
 * segurava a resposta HTTP — justamente o que ele deveria evitar.
 *
 * Evoluir por projeto: templates HTML versionados no repo em vez de texto
 * montado em Java.
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final UsuarioRepository usuarioRepository;
    private final String remetente;
    private final String baseUrl;

    public EmailServiceImpl(JavaMailSender mailSender,
                            UsuarioRepository usuarioRepository,
                            @Value("${app.mail.from:noreply@localhost}") String remetente,
                            @Value("${app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.usuarioRepository = usuarioRepository;
        this.remetente = remetente;
        this.baseUrl = baseUrl;
    }

    @Async
    @Override
    public void enviarBoasVindas(Usuario usuario, Empresa empresa) {
        enviar(usuario.getEmail(),
                "Bem-vindo!",
                """
                Ola, %s!

                A conta da %s esta pronta. Seu periodo de teste vai ate %s.

                Acesse: %s
                """.formatted(usuario.getNome(), empresa.getRazaoSocial(),
                        empresa.getTrialExpiraEm(), baseUrl));
    }

    @Async
    @Override
    public void enviarConvite(Usuario usuario, String tokenBruto) {
        enviar(usuario.getEmail(),
                "Voce foi convidado",
                """
                Ola, %s!

                Voce foi convidado para o sistema. Defina sua senha em ate 7 dias:
                %s/aceitar-convite?token=%s
                """.formatted(usuario.getNome(), baseUrl, tokenBruto));
    }

    @Async
    @Override
    public void enviarResetDeSenha(Usuario usuario, String tokenBruto) {
        enviar(usuario.getEmail(),
                "Redefinicao de senha",
                """
                Recebemos um pedido para redefinir sua senha. O link vale por 30 minutos:
                %s/redefinir-senha?token=%s

                Se nao foi voce, ignore este e-mail — sua senha continua a mesma.
                """.formatted(baseUrl, tokenBruto));
    }

    @Async
    @Override
    public void enviarAvisoDeCobranca(Empresa empresa, int etapa, String mensagem) {
        enviarAoResponsavel(empresa, "Pendencia no pagamento",
                """
                %s

                Regularize em: %s/plano
                """.formatted(mensagem, baseUrl));
    }

    @Async
    @Override
    public void enviarSuspensao(Empresa empresa) {
        enviarAoResponsavel(empresa, "Conta suspensa",
                """
                Sua conta foi suspensa por falta de pagamento.

                Nenhum dado foi excluido. Assim que o pagamento for confirmado,
                o acesso volta automaticamente: %s/plano
                """.formatted(baseUrl));
    }

    @Async
    @Override
    public void enviarCancelamento(Empresa empresa) {
        enviarAoResponsavel(empresa, "Assinatura cancelada",
                """
                Sua assinatura foi cancelada.

                Seus dados ficam disponiveis para exportacao ate %s.
                """.formatted(empresa.getPurgeEm()));
    }

    /**
     * Aviso de cobranca vai para TODOS os TENANT_ADMIN da empresa: mandar so
     * para um significa perder o cliente porque a pessoa saiu de ferias.
     */
    private void enviarAoResponsavel(Empresa empresa, String assunto, String corpo) {
        var admins = usuarioRepository.buscarAdminsDaEmpresa(empresa.getId());
        if (admins.isEmpty()) {
            // Nao deveria acontecer (o signup cria um), mas silencio aqui seria
            // um cliente suspenso sem nunca ter sido avisado.
            log.error("Empresa {} sem TENANT_ADMIN ativo — aviso '{}' nao enviado",
                    empresa.getId(), assunto);
            return;
        }
        admins.forEach(email -> enviar(email, assunto, corpo));
    }

    /**
     * Tolerante a falha: provedor de e-mail fora do ar nao pode derrubar um
     * signup nem travar a regua de dunning. A falha vai para o log (e para o
     * alerta), nao para a cara do usuario.
     */
    private void enviar(String destino, String assunto, String corpo) {
        try {
            var mensagem = new SimpleMailMessage();
            mensagem.setFrom(remetente);
            mensagem.setTo(destino);
            mensagem.setSubject(assunto);
            mensagem.setText(corpo);
            mailSender.send(mensagem);
        } catch (MailException e) {
            // Nunca logar o corpo: pode conter token de reset de senha.
            log.error("Falha ao enviar e-mail '{}'", assunto, e);
        }
    }
}
