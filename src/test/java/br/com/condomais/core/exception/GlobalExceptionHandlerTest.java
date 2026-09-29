package br.com.condomais.core.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/condominios/torres/123");

    @Test
    void naoEncontradoVira404ComMensagemDoService() {
        var resposta = handler.naoEncontrado(new RecursoNaoEncontradoException("Torre não encontrada."), request);

        assertEquals(404, resposta.getStatusCode().value());
        assertEquals(404, resposta.getBody().status());
        assertEquals("Torre não encontrada.", resposta.getBody().mensagem());
        assertEquals("/condominios/torres/123", resposta.getBody().caminho());
    }

    @Test
    void orElseThrowSemMensagemVira404() {
        var resposta = handler.naoEncontradoSemMensagem(new NoSuchElementException(), request);

        assertEquals(404, resposta.getStatusCode().value());
        assertEquals("Registro não encontrado.", resposta.getBody().mensagem());
    }

    @Test
    void conflitoVira409() {
        var resposta = handler.conflito(new ConflitoException("Não é possível excluir a torre: existem apartamentos vinculados."), request);

        assertEquals(409, resposta.getStatusCode().value());
    }

    @Test
    void regraDeNegocioVira400() {
        var resposta = handler.requisicaoInvalida(new IllegalArgumentException("A prioridade deve ser NORMAL ou URGENTE."), request);

        assertEquals(400, resposta.getStatusCode().value());
        assertEquals("A prioridade deve ser NORMAL ou URGENTE.", resposta.getBody().mensagem());
    }

    @Test
    void falhaDeLoginVira401SemRevelarOMotivo() {
        var resposta = handler.naoAutenticado(new BadCredentialsException("Bad credentials"), request);

        assertEquals(401, resposta.getStatusCode().value());
        assertEquals("CPF ou senha inválidos.", resposta.getBody().mensagem());
    }

    @Test
    void acessoAOutroCondominioVira403() {
        var resposta = handler.acessoNegado(new SecurityException("Acesso negado a dados de outro condomínio."), request);

        assertEquals(403, resposta.getStatusCode().value());
    }

    @Test
    void excecaoDoSpringMantemOStatusOriginal() {
        var resposta = handler.erroInesperado(new HttpRequestMethodNotSupportedException(HttpMethod.PATCH.name()), request);

        assertEquals(405, resposta.getStatusCode().value());
    }

    @Test
    void erroInesperadoVira500SemVazarDetalhes() {
        var resposta = handler.erroInesperado(new RuntimeException("detalhe interno do banco"), request);

        assertEquals(500, resposta.getStatusCode().value());
        assertEquals("Erro interno ao processar a requisição.", resposta.getBody().mensagem());
    }
}
