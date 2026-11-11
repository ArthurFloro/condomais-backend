package br.com.condomais.core.email;

import br.com.condomais.core.exception.ServicoIndisponivelException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EmailServiceTest {

    @Test
    void enviaPelaApiDoBrevoComAChaveNoHeader() {
        var builder = RestClient.builder();
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var service = new EmailService(" chave-brevo ", "equipe@exemplo.com", "Condo+", null, builder);
        servidor.expect(requestTo(EmailService.URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "chave-brevo"))
                .andExpect(jsonPath("$.sender.email").value("equipe@exemplo.com"))
                .andExpect(jsonPath("$.sender.name").value("Condo+"))
                .andExpect(jsonPath("$.to[0].email").value("morador@exemplo.com"))
                .andExpect(jsonPath("$.to[0].name").value("Ana Lima"))
                .andExpect(jsonPath("$.subject").value("Assunto"))
                .andExpect(jsonPath("$.htmlContent").value("<p>oi</p>"))
                .andExpect(jsonPath("$.textContent").value("oi"))
                .andRespond(withSuccess("{\"messageId\":\"x\"}", MediaType.APPLICATION_JSON));

        service.enviar("morador@exemplo.com", "Ana Lima", "Assunto", "<p>oi</p>", "oi");
        servidor.verify();
    }

    @Test
    void semChaveOuRemetenteFicaDesligado() {
        assertFalse(new EmailService("", "equipe@exemplo.com", null, null, RestClient.builder()).habilitado());
        assertFalse(new EmailService("chave", " ", null, null, RestClient.builder()).habilitado());
        var desligado = new EmailService(null, null, null, null, RestClient.builder());
        assertThrows(ServicoIndisponivelException.class, () -> desligado.enviar("a@b.com", "A", "s", "h", "t"));
    }

    @Test
    void erroDoBrevoViraServicoIndisponivel() {
        var builder = RestClient.builder();
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var service = new EmailService("chave", "equipe@exemplo.com", "Condo+", null, builder);
        servidor.expect(requestTo(EmailService.URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"invalid_parameter\",\"message\":\"sender not valid\"}"));

        assertThrows(ServicoIndisponivelException.class, () -> service.enviar("a@b.com", "A", "s", "h", "t"));
    }
}
