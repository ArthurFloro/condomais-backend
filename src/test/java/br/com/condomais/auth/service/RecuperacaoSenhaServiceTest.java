package br.com.condomais.auth.service;

import br.com.condomais.auth.model.RecuperacaoSenha;
import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.RecuperacaoSenhaRepository;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.core.email.EmailService;
import br.com.condomais.core.exception.ServicoIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    private static final String CPF = "529.982.247-25";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RecuperacaoSenhaRepository recuperacaoRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    private RecuperacaoSenhaService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new RecuperacaoSenhaService(usuarioRepository, recuperacaoRepository, passwordEncoder, emailService,
                "", "https://condo-mais.vercel.app/, http://localhost:5173");
        var condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Ana Lima");
        usuario.setCpf(CPF);
        usuario.setEmail("ana@exemplo.com");
        usuario.setPerfil("MORADOR");
        usuario.setCondominio(condominio);
    }

    private String pedirEPegarToken() {
        when(emailService.habilitado()).thenReturn(true);
        when(usuarioRepository.findByCpf(CPF)).thenReturn(Optional.of(usuario));
        service.solicitar(CPF);
        var texto = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("ana@exemplo.com"), eq("Ana Lima"), anyString(), anyString(), texto.capture());
        var link = Pattern.compile("https://condo-mais\\.vercel\\.app/recuperar-senha/nova-senha\\?token=([A-Za-z0-9_-]{43})").matcher(texto.getValue());
        assertTrue(link.find(), texto.getValue());
        return link.group(1);
    }

    // ---------------------------------------------------------------------
    // Pedido
    // ---------------------------------------------------------------------

    @Test
    void pedidoGuardaSoOHashEEnviaOLinkParaOFront() {
        var token = pedirEPegarToken();

        var salvo = ArgumentCaptor.forClass(RecuperacaoSenha.class);
        verify(recuperacaoRepository).save(salvo.capture());
        assertEquals(RecuperacaoSenhaService.hash(token), salvo.getValue().getTokenHash());
        assertNotEquals(token, salvo.getValue().getTokenHash());
        assertSame(usuario, salvo.getValue().getUsuario());
        assertEquals(RecuperacaoSenhaService.VALIDADE.toMinutes(),
                java.time.Duration.between(salvo.getValue().getCriadoEm(), salvo.getValue().getExpiraEm()).toMinutes());
        assertNull(salvo.getValue().getUsadoEm());
    }

    @Test
    void cadaPedidoGeraUmTokenDiferente() {
        when(emailService.habilitado()).thenReturn(true);
        when(usuarioRepository.findByCpf(CPF)).thenReturn(Optional.of(usuario));
        service.solicitar(CPF);
        service.solicitar(CPF);
        var salvo = ArgumentCaptor.forClass(RecuperacaoSenha.class);
        verify(recuperacaoRepository, times(2)).save(salvo.capture());
        assertNotEquals(salvo.getAllValues().get(0).getTokenHash(), salvo.getAllValues().get(1).getTokenHash());
    }

    @Test
    void cpfDesconhecidoSemEmailOuInativoNaoEnviaNadaESemErro() {
        when(emailService.habilitado()).thenReturn(true);
        when(usuarioRepository.findByCpf(anyString())).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.solicitar("111.444.777-35"));

        usuario.setEmail(" ");
        when(usuarioRepository.findByCpf(CPF)).thenReturn(Optional.of(usuario));
        assertDoesNotThrow(() -> service.solicitar(CPF));

        usuario.setEmail("ana@exemplo.com");
        usuario.setStatus("INATIVO");
        assertDoesNotThrow(() -> service.solicitar(CPF));

        usuario.setStatus("ATIVO");
        usuario.getCondominio().setStatus("INATIVO");
        assertDoesNotThrow(() -> service.solicitar(CPF));

        verify(recuperacaoRepository, never()).save(any());
        verify(emailService, never()).enviar(any(), any(), any(), any(), any());
    }

    @Test
    void maisDeTresPedidosNaHoraSaoIgnorados() {
        when(emailService.habilitado()).thenReturn(true);
        when(usuarioRepository.findByCpf(CPF)).thenReturn(Optional.of(usuario));
        when(recuperacaoRepository.countByUsuarioIdAndCriadoEmAfter(eq(usuario.getId()), any())).thenReturn(3L);

        assertDoesNotThrow(() -> service.solicitar(CPF));
        verify(recuperacaoRepository, never()).save(any());
        verify(emailService, never()).enviar(any(), any(), any(), any(), any());
    }

    @Test
    void falhaNoEnvioNaoMudaARespostaParaNaoRevelarOCpf() {
        when(emailService.habilitado()).thenReturn(true);
        when(usuarioRepository.findByCpf(CPF)).thenReturn(Optional.of(usuario));
        doThrow(new ServicoIndisponivelException("fora")).when(emailService).enviar(any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> service.solicitar(CPF));
    }

    @Test
    void semEmailConfiguradoRespondeIndisponivelESemCpfEhErro() {
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(" "));
        when(emailService.habilitado()).thenReturn(false);
        assertThrows(ServicoIndisponivelException.class, () -> service.solicitar(CPF));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void emailTemACaraDaMarcaEEscapaONomeEOLink() throws Exception {
        var html = service.html("<b>Ana</b> Lima", "https://condo-mais.vercel.app/recuperar-senha/nova-senha?token=abc&x=1");

        assertTrue(html.contains("#0b2a53"), "azul da marca");
        assertTrue(html.contains("#08783b"), "verde da marca no botão");
        assertTrue(html.contains("src=\"https://condo-mais.vercel.app/logo-principal-branca.png\""), "logo do front publicado");
        assertTrue(html.contains("alt=\"Condo+\""), "texto quando a imagem é bloqueada");
        assertTrue(html.contains("Olá, &lt;b&gt;Ana&lt;/b&gt;,"), "nome escapado");
        assertFalse(html.contains("<b>Ana</b>"));
        assertTrue(html.contains("href=\"https://condo-mais.vercel.app/recuperar-senha/nova-senha?token=abc&amp;x=1\""), "link escapado");
        assertTrue(html.contains("30 minutos"));
        // Prévia para olhar no navegador: mvnw test -Dprevia.email=caminho.html
        var previa = System.getProperty("previa.email");
        if (previa != null) java.nio.file.Files.writeString(java.nio.file.Path.of(previa),
                service.html("Ana Lima", "https://condo-mais.vercel.app/recuperar-senha/nova-senha?token=EXEMPLO"));
    }

    // ---------------------------------------------------------------------
    // Redefinição
    // ---------------------------------------------------------------------

    private RecuperacaoSenha pedido(String token, LocalDateTime expira, LocalDateTime usado) {
        var pedido = new RecuperacaoSenha();
        pedido.setUsuario(usuario);
        pedido.setTokenHash(RecuperacaoSenhaService.hash(token));
        pedido.setCriadoEm(expira.minusMinutes(30));
        pedido.setExpiraEm(expira);
        pedido.setUsadoEm(usado);
        return pedido;
    }

    @Test
    void linkValidoTrocaASenhaEInvalidaOsOutros() {
        var agora = LocalDateTime.now(FUSO);
        when(recuperacaoRepository.findByTokenHash(RecuperacaoSenhaService.hash("tok"))).thenReturn(Optional.of(pedido("tok", agora.plusMinutes(10), null)));
        when(passwordEncoder.encode("NovaSenha@1")).thenReturn("hash-bcrypt");

        service.redefinir(" tok ", "NovaSenha@1");

        assertEquals("hash-bcrypt", usuario.getSenha());
        verify(usuarioRepository).save(usuario);
        verify(recuperacaoRepository).invalidarAbertas(eq(usuario.getId()), any());
    }

    @Test
    void linkExpiradoUsadoOuDesconhecidoEhRecusado() {
        var agora = LocalDateTime.now(FUSO);
        when(recuperacaoRepository.findByTokenHash(RecuperacaoSenhaService.hash("velho"))).thenReturn(Optional.of(pedido("velho", agora.minusMinutes(1), null)));
        when(recuperacaoRepository.findByTokenHash(RecuperacaoSenhaService.hash("usado"))).thenReturn(Optional.of(pedido("usado", agora.plusMinutes(10), agora)));
        when(recuperacaoRepository.findByTokenHash(RecuperacaoSenhaService.hash("outro"))).thenReturn(Optional.empty());

        for (var token : new String[]{"velho", "usado", "outro", " "}) {
            var erro = assertThrows(IllegalArgumentException.class, () -> service.redefinir(token, "NovaSenha@1"));
            assertEquals(RecuperacaoSenhaService.LINK_INVALIDO, erro.getMessage());
        }
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void usuarioDesativadoDepoisDoPedidoNaoRedefine() {
        var agora = LocalDateTime.now(FUSO);
        usuario.setStatus("INATIVO");
        when(recuperacaoRepository.findByTokenHash(RecuperacaoSenhaService.hash("tok"))).thenReturn(Optional.of(pedido("tok", agora.plusMinutes(10), null)));

        assertThrows(IllegalArgumentException.class, () -> service.redefinir("tok", "NovaSenha@1"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void senhaForaDasRegrasEhRecusadaAntesDeOlharOLink() {
        assertEquals("A senha deve ter no mínimo 8 caracteres.",
                assertThrows(IllegalArgumentException.class, () -> service.redefinir("tok", "curta")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.redefinir("tok", "ç".repeat(37)));
        verifyNoInteractions(recuperacaoRepository);
    }
}
