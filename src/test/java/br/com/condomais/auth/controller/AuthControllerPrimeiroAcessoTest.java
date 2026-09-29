package br.com.condomais.auth.controller;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerPrimeiroAcessoTest {

    private static final String CPF = "12345678900";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthController controller;

    @Test
    void criaSenhaQuandoUsuarioAindaNaoTemSenha() {
        var usuario = new Usuario();
        usuario.setCpf(CPF);
        when(usuarioRepository.findByCpf(CPF)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("nova-senha")).thenReturn("hash");

        var resposta = controller.primeiroAcesso(new AuthController.PrimeiroAcessoDTO(CPF, "nova-senha"));

        assertEquals(200, resposta.getStatusCode().value());
        assertEquals("hash", usuario.getSenha());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void recusaQuandoUsuarioJaTemSenha() {
        var usuario = new Usuario();
        usuario.setCpf(CPF);
        usuario.setSenha("hash-existente");
        when(usuarioRepository.findByCpf(CPF)).thenReturn(Optional.of(usuario));

        var resposta = controller.primeiroAcesso(new AuthController.PrimeiroAcessoDTO(CPF, "senha-do-atacante"));

        assertEquals(403, resposta.getStatusCode().value());
        assertEquals("hash-existente", usuario.getSenha());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cpfInexistenteRecebeAMesmaRespostaDeQuemJaTemSenha() {
        var usuario = new Usuario();
        usuario.setSenha("hash-existente");
        when(usuarioRepository.findByCpf(CPF)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByCpf("00000000000")).thenReturn(Optional.empty());

        var jaTemSenha = controller.primeiroAcesso(new AuthController.PrimeiroAcessoDTO(CPF, "x"));
        var inexistente = controller.primeiroAcesso(new AuthController.PrimeiroAcessoDTO("00000000000", "x"));

        assertEquals(jaTemSenha.getStatusCode(), inexistente.getStatusCode());
        assertEquals(jaTemSenha.getBody(), inexistente.getBody());
    }

    @Test
    void recusaSenhaEmBranco() {
        var resposta = controller.primeiroAcesso(new AuthController.PrimeiroAcessoDTO(CPF, "  "));

        assertEquals(400, resposta.getStatusCode().value());
        verify(usuarioRepository, never()).save(any());
    }
}
