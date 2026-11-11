package br.com.condomais.core.email;

import br.com.condomais.core.exception.ServicoIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envio de e-mail transacional pela API HTTP do Brevo (https://developers.brevo.com/reference/sendtransacemail).
 * HTTP e não SMTP porque o plano gratuito do Render bloqueia as portas de SMTP. Sem BREVO_API_KEY e
 * EMAIL_REMETENTE o envio fica desligado e quem depende dele responde 503.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    static final String URL = "https://api.brevo.com/v3/smtp/email";

    private final String url;
    private final String apiKey;
    private final String remetente;
    private final String remetenteNome;
    private final RestClient client;

    @Autowired
    public EmailService(@Value("${email.brevo.api-key:}") String apiKey,
                        @Value("${email.remetente:}") String remetente,
                        @Value("${email.remetente-nome:Condo+}") String remetenteNome,
                        @Value("${email.brevo.url:}") String url) {
        this(apiKey, remetente, remetenteNome, url, RestClient.builder().requestFactory(fabrica()));
    }

    // Os testes entregam um builder já ligado ao MockRestServiceServer
    EmailService(String apiKey, String remetente, String remetenteNome, String url, RestClient.Builder builder) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.remetente = remetente == null ? "" : remetente.trim();
        this.remetenteNome = remetenteNome == null || remetenteNome.isBlank() ? "Condo+" : remetenteNome.trim();
        // email.brevo.url só serve para testar contra um servidor falso local; vazio = API do Brevo
        this.url = url == null || url.isBlank() ? URL : url.trim();
        this.client = builder.build();
        if (!habilitado()) {
            log.warn("Envio de e-mail desligado: defina BREVO_API_KEY e EMAIL_REMETENTE para ligar a recuperação de senha.");
        } else if (!URL.equals(this.url)) {
            log.warn("Envio de e-mail apontando para {} em vez da API do Brevo.", this.url);
        }
    }

    private static SimpleClientHttpRequestFactory fabrica() {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(20));
        return fabrica;
    }

    public boolean habilitado() {
        return !apiKey.isEmpty() && !remetente.isEmpty();
    }

    public void enviar(String para, String nome, String assunto, String html, String texto) {
        if (!habilitado()) {
            throw new ServicoIndisponivelException("O envio de e-mail não está habilitado neste servidor.");
        }
        var destinatario = nome == null || nome.isBlank() ? Map.of("email", para) : Map.of("email", para, "name", nome);
        try {
            client.post()
                    .uri(url)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "sender", Map.of("email", remetente, "name", remetenteNome),
                            "to", List.of(destinatario),
                            "subject", assunto,
                            "htmlContent", html,
                            "textContent", texto))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // A chave nunca vai para o log; o corpo da resposta do Brevo explica o motivo (remetente não validado etc.)
            log.error("Falha ao enviar e-mail pelo Brevo: {}", e.getMessage());
            throw new ServicoIndisponivelException("Não foi possível enviar o e-mail agora. Tente novamente mais tarde.");
        }
    }
}
