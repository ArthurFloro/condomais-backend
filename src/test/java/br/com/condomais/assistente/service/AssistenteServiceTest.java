package br.com.condomais.assistente.service;

import br.com.condomais.assistente.dto.MensagemAssistenteDTO;
import br.com.condomais.auth.model.Usuario;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.core.exception.ServicoIndisponivelException;
import br.com.condomais.core.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AssistenteServiceTest {

    private static final String URL = "http://assistente.test";
    private static final String TOKEN = "Bearer token-do-morador";
    private static final List<MensagemAssistenteDTO> PERGUNTA = List.of(new MensagemAssistenteDTO("USUARIO", "Tem encomenda?"));

    private MockRestServiceServer servidor;
    private AssistenteService service;
    private UsuarioAutenticado morador;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        service = new AssistenteService(URL + "/", builder);
        morador = autenticado("MORADOR");
    }

    private static UsuarioAutenticado autenticado(String perfil) {
        var condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setPerfil(perfil);
        usuario.setCondominio(condominio);
        return new UsuarioAutenticado(usuario);
    }

    private static String erro(int status, String mensagem) {
        return "{\"status\":" + status + ",\"erro\":\"x\",\"mensagem\":\"" + mensagem + "\",\"caminho\":\"/assistente/mensagens\"}";
    }

    @Test
    void repassaConversaComOTokenDoMoradorEDevolveARespostaDoServicoPython() {
        servidor.expect(requestTo(URL + "/assistente/mensagens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", TOKEN))
                .andExpect(content().json("{\"mensagens\":[{\"papel\":\"USUARIO\",\"texto\":\"Tem encomenda?\"}]}"))
                .andRespond(withSuccess("{\"resposta\":\"Você tem 1 encomenda.\"}", MediaType.APPLICATION_JSON));

        var resposta = service.responder(PERGUNTA, morador, TOKEN);

        assertEquals("Você tem 1 encomenda.", resposta.resposta());
        servidor.verify();
    }

    @Test
    void semUrlConfiguradaFicaDesligadoSemChamarOServico() {
        var desligado = new AssistenteService("  ", RestClient.builder());

        var erro = assertThrows(ServicoIndisponivelException.class, () -> desligado.responder(PERGUNTA, morador, TOKEN));

        assertTrue(erro.getMessage().contains("não está habilitado"));
    }

    @Test
    void apenasMoradorUsaOAssistenteESemChamarOServico() {
        assertThrows(SecurityException.class, () -> service.responder(PERGUNTA, autenticado("ADMIN"), TOKEN));
        assertThrows(SecurityException.class, () -> service.responder(PERGUNTA, autenticado(null), TOKEN));
        servidor.verify();
    }

    @Test
    void conversaVaziaEhRejeitadaSemChamarOServico() {
        assertThrows(IllegalArgumentException.class, () -> service.responder(List.of(), morador, TOKEN));
        assertThrows(IllegalArgumentException.class, () -> service.responder(null, morador, TOKEN));
        servidor.verify();
    }

    @Test
    void erro400DoServicoViraRequisicaoInvalidaComAMensagemDele() {
        servidor.expect(requestTo(URL + "/assistente/mensagens"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body(erro(400, "A última mensagem deve ser do morador.")));

        var e = assertThrows(IllegalArgumentException.class, () -> service.responder(PERGUNTA, morador, TOKEN));

        assertEquals("A última mensagem deve ser do morador.", e.getMessage());
    }

    @Test
    void erro403DoServicoViraAcessoNegado() {
        servidor.expect(requestTo(URL + "/assistente/mensagens"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body(erro(403, "Somente moradores.")));

        assertThrows(SecurityException.class, () -> service.responder(PERGUNTA, morador, TOKEN));
    }

    @Test
    void limiteEIndisponibilidadeDoServicoViram503ComAMensagemDele() {
        servidor.expect(requestTo(URL + "/assistente/mensagens"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body(erro(429, "Você fez muitas perguntas em pouco tempo.")));

        var e = assertThrows(ServicoIndisponivelException.class, () -> service.responder(PERGUNTA, morador, TOKEN));

        assertEquals("Você fez muitas perguntas em pouco tempo.", e.getMessage());
    }

    @Test
    void erro401OuErroInternoDoServicoViram503GenericoSemVazarDetalhes() {
        servidor.expect(requestTo(URL + "/assistente/mensagens"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body(erro(401, "Sessão inválida")));
        servidor.expect(requestTo(URL + "/assistente/mensagens"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("Traceback (most recent call last)"));

        for (int i = 0; i < 2; i++) {
            var e = assertThrows(ServicoIndisponivelException.class, () -> service.responder(PERGUNTA, morador, TOKEN));
            assertEquals("O assistente está indisponível no momento. Tente novamente mais tarde.", e.getMessage());
        }
    }

    @Test
    void servicoForaDoArViraIndisponivel() {
        servidor.expect(requestTo(URL + "/assistente/mensagens"))
                .andRespond(request -> { throw new IOException("Connection refused"); });

        assertThrows(ServicoIndisponivelException.class, () -> service.responder(PERGUNTA, morador, TOKEN));
    }

    @Test
    void respostaSemCorpoViraIndisponivel() {
        servidor.expect(requestTo(URL + "/assistente/mensagens"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(ServicoIndisponivelException.class, () -> service.responder(PERGUNTA, morador, TOKEN));
    }
}
