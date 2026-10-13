package br.com.condomais.assistente.service;

import br.com.condomais.assistente.dto.ConversaAssistenteDTO;
import br.com.condomais.assistente.dto.MensagemAssistenteDTO;
import br.com.condomais.assistente.dto.RespostaAssistenteDTO;
import br.com.condomais.core.exception.ServicoIndisponivelException;
import br.com.condomais.core.security.UsuarioAutenticado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Assistente virtual do morador. A inteligência (Claude API, ferramentas de consulta, prompt) vive
 * no serviço Python <code>condomais-assistente</code>; aqui o backend só confere o perfil e repassa a
 * conversa com o mesmo token do morador, que o serviço Python valida de novo e usa para escopar os dados.
 */
@Service
public class AssistenteService {

    private static final Logger log = LoggerFactory.getLogger(AssistenteService.class);

    private static final String INDISPONIVEL = "O assistente está indisponível no momento. Tente novamente mais tarde.";
    // Maior que o limite de 90s do serviço Python, para o erro dele chegar antes do nosso timeout
    private static final Duration LEITURA = Duration.ofSeconds(100);
    // O serviço Python no plano gratuito hiberna: a primeira conexão espera ele acordar
    private static final Duration CONEXAO = Duration.ofSeconds(30);

    private final String url;
    private final RestClient client;

    @Autowired
    public AssistenteService(@Value("${assistente.api.url:}") String url) {
        this(url, RestClient.builder().requestFactory(fabrica()));
    }

    // Os testes entregam um builder já ligado ao MockRestServiceServer
    AssistenteService(String url, RestClient.Builder builder) {
        this.url = url == null ? "" : url.trim().replaceAll("/+$", "");
        this.client = builder.build();
        if (this.url.isEmpty()) {
            log.warn("Assistente virtual desligado: defina ASSISTENTE_API_URL com o endereço do serviço condomais-assistente.");
        }
    }

    private static SimpleClientHttpRequestFactory fabrica() {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(CONEXAO);
        fabrica.setReadTimeout(LEITURA);
        return fabrica;
    }

    /**
     * @param authorization valor do header Authorization da requisição (o token do próprio morador)
     */
    public RespostaAssistenteDTO responder(List<MensagemAssistenteDTO> mensagens, UsuarioAutenticado auth, String authorization) {
        if (url.isEmpty()) {
            throw new ServicoIndisponivelException("O assistente virtual não está habilitado neste servidor.");
        }
        if (!"MORADOR".equalsIgnoreCase(auth.getPerfil() == null ? "" : auth.getPerfil().trim())) {
            throw new SecurityException("O assistente virtual está disponível apenas para moradores.");
        }
        if (mensagens == null || mensagens.isEmpty()) {
            throw new IllegalArgumentException("Envie ao menos uma mensagem.");
        }

        try {
            var resposta = client.post()
                    .uri(url + "/assistente/mensagens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(new ConversaAssistenteDTO(mensagens))
                    .retrieve()
                    .body(RespostaAssistenteDTO.class);
            if (resposta == null || resposta.resposta() == null) {
                log.error("Serviço do assistente respondeu sem corpo");
                throw new ServicoIndisponivelException(INDISPONIVEL);
            }
            return resposta;
        } catch (RestClientResponseException e) {
            throw traduzir(e);
        } catch (ResourceAccessException e) {
            log.error("Não foi possível falar com o serviço do assistente em {}", url, e);
            throw new ServicoIndisponivelException(INDISPONIVEL);
        }
    }

    /**
     * Erros de negócio do serviço Python voltam com a mesma semântica; o resto (inclusive 401, que aqui
     * indica JWT_SECRET diferente entre os dois serviços) vira 503 e fica no log.
     */
    private RuntimeException traduzir(RestClientResponseException e) {
        var status = e.getStatusCode().value();
        var mensagem = mensagemDe(e);
        return switch (status) {
            case 400 -> new IllegalArgumentException(mensagem != null ? mensagem : "Requisição inválida.");
            case 403 -> new SecurityException(mensagem != null ? mensagem : "Acesso negado ao assistente virtual.");
            case 429, 503 -> new ServicoIndisponivelException(mensagem != null ? mensagem : INDISPONIVEL);
            default -> {
                log.error("Serviço do assistente respondeu {}: {}", status, e.getResponseBodyAsString());
                yield new ServicoIndisponivelException(INDISPONIVEL);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static String mensagemDe(RestClientResponseException e) {
        try {
            var corpo = e.getResponseBodyAs(Map.class);
            var mensagem = corpo == null ? null : corpo.get("mensagem");
            return mensagem instanceof String texto && !texto.isBlank() ? texto : null;
        } catch (RuntimeException ignorada) {
            return null;
        }
    }
}
