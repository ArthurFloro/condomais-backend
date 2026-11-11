package br.com.condomais.auth.service;

import br.com.condomais.auth.model.RecuperacaoSenha;
import br.com.condomais.auth.repository.RecuperacaoSenhaRepository;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.core.email.EmailService;
import br.com.condomais.core.exception.ServicoIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Recuperação de senha por e-mail: o usuário informa o CPF, recebe no e-mail cadastrado um link de uso único
 * que vale 30 minutos e escolhe a nova senha. A resposta do pedido é sempre a mesma, para não revelar quais
 * CPFs existem nem quais têm e-mail.
 */
@Service
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    static final Duration VALIDADE = Duration.ofMinutes(30);
    // Pedidos por usuário a cada hora: evita encher a caixa de alguém e gastar a cota diária do Brevo
    static final int MAXIMO_POR_HORA = 3;
    public static final String RESPOSTA_PEDIDO = "Se o CPF estiver cadastrado com um e-mail, enviamos um link para criar uma nova senha. "
            + "Ele vale por 30 minutos. Se não receber, confira a caixa de spam ou procure a administração do condomínio.";
    static final String LINK_INVALIDO = "Este link de recuperação é inválido ou expirou. Peça um novo.";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final RecuperacaoSenhaRepository recuperacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String frontUrl;

    @Autowired
    public RecuperacaoSenhaService(UsuarioRepository usuarioRepository, RecuperacaoSenhaRepository recuperacaoRepository,
                                   PasswordEncoder passwordEncoder, EmailService emailService,
                                   @Value("${app.front-url:}") String frontUrl,
                                   @Value("${api.cors.allowed-origins:http://localhost:5173}") String origens) {
        this.usuarioRepository = usuarioRepository;
        this.recuperacaoRepository = recuperacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        // Sem FRONT_URL, o link aponta para a primeira origem do CORS (o próprio front na Vercel)
        var base = frontUrl == null || frontUrl.isBlank() ? origens.split(",")[0] : frontUrl;
        this.frontUrl = base.trim().replaceAll("/+$", "");
    }

    /** Sem @Transactional: o envio do e-mail não segura conexão do banco (o pool de produção é pequeno). */
    public void solicitar(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            throw new IllegalArgumentException("Informe o CPF.");
        }
        if (!emailService.habilitado()) {
            throw new ServicoIndisponivelException("A recuperação de senha por e-mail não está disponível. Procure a administração do condomínio.");
        }
        var usuario = usuarioRepository.findByCpf(cpf.trim()).orElse(null);
        if (usuario == null || !usuario.isAtivo() || (usuario.getCondominio() != null && !usuario.getCondominio().isAtivo())
                || usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            return;
        }
        var agora = LocalDateTime.now(FUSO);
        if (recuperacaoRepository.countByUsuarioIdAndCriadoEmAfter(usuario.getId(), agora.minusHours(1)) >= MAXIMO_POR_HORA) {
            return;
        }

        var bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var pedido = new RecuperacaoSenha();
        pedido.setUsuario(usuario);
        pedido.setTokenHash(hash(token));
        pedido.setCriadoEm(agora);
        pedido.setExpiraEm(agora.plus(VALIDADE));
        recuperacaoRepository.save(pedido);

        var link = frontUrl + "/recuperar-senha/nova-senha?token=" + token;
        var nome = usuario.getNome() == null ? "" : usuario.getNome().trim();
        try {
            emailService.enviar(usuario.getEmail().trim(), nome, "Condo+: criar uma nova senha", html(nome, link), texto(nome, link));
        } catch (ServicoIndisponivelException e) {
            // Mesma resposta de sempre: um erro só para CPFs com e-mail revelaria quais existem. O motivo fica no log.
            log.warn("Recuperação de senha do usuário {} não enviada.", usuario.getId());
        }
    }

    @Transactional
    public void redefinir(String token, String novaSenha) {
        var problema = SenhaRegras.problema(novaSenha);
        if (problema != null) {
            throw new IllegalArgumentException(problema);
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(LINK_INVALIDO);
        }
        var agora = LocalDateTime.now(FUSO);
        var pedido = recuperacaoRepository.findByTokenHash(hash(token.trim()))
                .filter(p -> p.getUsadoEm() == null && p.getExpiraEm().isAfter(agora))
                .orElseThrow(() -> new IllegalArgumentException(LINK_INVALIDO));
        var usuario = pedido.getUsuario();
        if (!usuario.isAtivo() || (usuario.getCondominio() != null && !usuario.getCondominio().isAtivo())) {
            throw new IllegalArgumentException(LINK_INVALIDO);
        }
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
        recuperacaoRepository.invalidarAbertas(usuario.getId(), agora);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String saudacao(String nome) {
        return nome.isEmpty() ? "Olá," : "Olá, " + nome.split("\\s+")[0] + ",";
    }

    private static String texto(String nome, String link) {
        return saudacao(nome) + "\n\nRecebemos um pedido para criar uma nova senha no Condo+. Abra o link abaixo em até 30 minutos:\n\n"
                + link + "\n\nSe não foi você, ignore este e-mail: sua senha continua a mesma.";
    }

    // Cores da marca, as mesmas variáveis do front (--navy, --green, --muted)
    private static final String AZUL = "#0b2a53";
    private static final String VERDE = "#08783b";
    private static final String CINZA = "#59656f";

    /**
     * Layout em tabelas com estilos embutidos: é o que Gmail e Outlook exibem direito (sem CSS externo nem
     * &lt;style&gt;). O logo vem do próprio front publicado; se a imagem for bloqueada, aparece o texto "Condo+".
     */
    String html(String nome, String link) {
        var href = HtmlUtils.htmlEscape(link, "UTF-8");
        var logo = HtmlUtils.htmlEscape(frontUrl + "/logo-principal-branca.png", "UTF-8");
        var fonte = "font-family:Arial,Helvetica,sans-serif;";
        return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Criar nova senha</title></head>"
                + "<body style=\"margin:0;padding:0;background:#f2f4f7;\">"
                // Prévia que o Gmail mostra ao lado do assunto
                + "<div style=\"display:none;max-height:0;overflow:hidden;\">Seu link para criar uma nova senha no Condo+ vale por 30 minutos.</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f2f4f7;\"><tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;background:#ffffff;border-radius:12px;overflow:hidden;\">"
                + "<tr><td align=\"center\" style=\"background:" + AZUL + ";padding:24px;\">"
                + "<img src=\"" + logo + "\" width=\"150\" height=\"67\" alt=\"Condo+\" style=\"display:block;border:0;color:#ffffff;" + fonte + "font-size:24px;font-weight:bold;\">"
                + "</td></tr>"
                + "<tr><td style=\"padding:32px 32px 8px;" + fonte + "color:" + AZUL + ";\">"
                + "<h1 style=\"margin:0 0 16px;font-size:22px;\">Criar nova senha</h1>"
                + "<p style=\"margin:0 0 12px;font-size:16px;line-height:24px;\">" + HtmlUtils.htmlEscape(saudacao(nome), "UTF-8") + "</p>"
                + "<p style=\"margin:0 0 24px;font-size:16px;line-height:24px;\">Recebemos um pedido para criar uma nova senha no Condo+. "
                + "Use o botão abaixo em até <strong>30 minutos</strong>. O link funciona uma única vez.</p>"
                + "</td></tr>"
                + "<tr><td align=\"center\" style=\"padding:0 32px 24px;\">"
                + "<a href=\"" + href + "\" style=\"display:inline-block;background:" + VERDE + ";color:#ffffff;" + fonte
                + "font-size:16px;font-weight:bold;text-decoration:none;padding:14px 32px;border-radius:8px;\">Criar nova senha</a>"
                + "</td></tr>"
                + "<tr><td style=\"padding:0 32px 32px;" + fonte + "color:" + CINZA + ";font-size:13px;line-height:20px;\">"
                + "Se o botão não abrir, copie este endereço no navegador:<br>"
                + "<a href=\"" + href + "\" style=\"color:" + VERDE + ";word-break:break-all;\">" + href + "</a>"
                + "</td></tr>"
                + "<tr><td style=\"background:#f7f8fa;border-top:1px solid #e4e7eb;padding:20px 32px;" + fonte + "color:" + CINZA + ";font-size:13px;line-height:20px;\">"
                + "Se não foi você que pediu, ignore este e-mail: sua senha continua a mesma.<br>"
                + "Este é um e-mail automático do Condo+, não é preciso responder."
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }
}
