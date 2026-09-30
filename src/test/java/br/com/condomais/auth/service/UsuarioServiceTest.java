package br.com.condomais.auth.service;

import br.com.condomais.auth.dto.UsuarioDTO;
import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.model.Torre;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.condominio.repository.CondominioRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    // CPFs com dígitos verificadores válidos, gerados só para teste
    private static final String CPF_VALIDO = "529.982.247-25";
    private static final String OUTRO_CPF_VALIDO = "111.444.777-35";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ApartamentoRepository apartamentoRepository;
    @Mock private CondominioRepository condominioRepository;
    @InjectMocks private UsuarioService service;

    private Condominio condominio;
    private Usuario admin;
    private UsuarioAutenticado authAdmin;

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        admin = usuario("ADMIN");
        authAdmin = new UsuarioAutenticado(admin);
    }

    private Usuario usuario(String perfil) {
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Fulano");
        usuario.setPerfil(perfil);
        usuario.setCondominio(condominio);
        return usuario;
    }

    private Apartamento apartamento() {
        var torre = new Torre();
        torre.setNome("Torre A");
        var apartamento = new Apartamento();
        apartamento.setId(UUID.randomUUID());
        apartamento.setNumero("101");
        apartamento.setTorre(torre);
        apartamento.setCondominio(condominio);
        return apartamento;
    }

    private UsuarioDTO morador(String cpf, UUID apartamentoId) {
        return new UsuarioDTO("Maria Souza", cpf, "maria@exemplo.com", "(11) 90000-0000", "MORADOR", apartamentoId, "proprietario");
    }

    @Test
    void cadastraMoradorSemSenhaComCpfFormatadoEUnidadeDoCondominio() {
        var apartamento = apartamento();
        when(condominioRepository.getReferenceById(condominio.getId())).thenReturn(condominio);
        when(usuarioRepository.findByCpf(CPF_VALIDO)).thenReturn(Optional.empty());
        when(apartamentoRepository.findByIdAndCondominioId(apartamento.getId(), condominio.getId())).thenReturn(Optional.of(apartamento));
        when(usuarioRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        var resposta = service.cadastrar(morador("52998224725", apartamento.getId()), authAdmin);

        assertEquals(CPF_VALIDO, resposta.cpf());
        assertEquals("MORADOR", resposta.perfil());
        assertEquals("PROPRIETARIO", resposta.vinculo());
        assertEquals("ATIVO", resposta.status());
        assertEquals("101", resposta.apartamentoNumero());
        assertEquals("Torre A", resposta.torreNome());
        assertTrue(resposta.primeiroAcessoPendente());
    }

    @Test
    void naoAdminNaoGerenciaUsuarios() {
        var authMorador = new UsuarioAutenticado(usuario("MORADOR"));

        assertThrows(SecurityException.class, () -> service.listar(authMorador));
        assertThrows(SecurityException.class, () -> service.cadastrar(morador(CPF_VALIDO, UUID.randomUUID()), authMorador));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void perfilAntigoAdministradorContaComoAdmin() {
        var authAntigo = new UsuarioAutenticado(usuario("Administrador"));
        when(usuarioRepository.findByCondominioIdOrderByNomeAsc(condominio.getId())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.listar(authAntigo));
    }

    @Test
    void recusaCpfInvalido() {
        for (var cpf : new String[]{"123.456.789-00", "111.111.111-11", "1234", "", null}) {
            var erro = assertThrows(IllegalArgumentException.class, () -> service.cadastrar(morador(cpf, UUID.randomUUID()), authAdmin));
            assertEquals("CPF inválido.", erro.getMessage());
        }
    }

    @Test
    void cpfDuplicadoEhConflito() {
        when(condominioRepository.getReferenceById(condominio.getId())).thenReturn(condominio);
        when(usuarioRepository.findByCpf(CPF_VALIDO)).thenReturn(Optional.of(usuario("MORADOR")));

        assertThrows(ConflitoException.class, () -> service.cadastrar(morador(CPF_VALIDO, UUID.randomUUID()), authAdmin));
    }

    @Test
    void moradorPrecisaDeUnidadeDoProprioCondominio() {
        when(condominioRepository.getReferenceById(condominio.getId())).thenReturn(condominio);
        when(usuarioRepository.findByCpf(CPF_VALIDO)).thenReturn(Optional.empty());

        var semUnidade = assertThrows(IllegalArgumentException.class, () -> service.cadastrar(morador(CPF_VALIDO, null), authAdmin));
        assertEquals("Informe a unidade do morador.", semUnidade.getMessage());

        var outroCondominio = UUID.randomUUID();
        when(apartamentoRepository.findByIdAndCondominioId(outroCondominio, condominio.getId())).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.cadastrar(morador(CPF_VALIDO, outroCondominio), authAdmin));
    }

    @Test
    void portariaNaoGuardaUnidade() {
        when(condominioRepository.getReferenceById(condominio.getId())).thenReturn(condominio);
        when(usuarioRepository.findByCpf(OUTRO_CPF_VALIDO)).thenReturn(Optional.empty());
        when(usuarioRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        var resposta = service.cadastrar(new UsuarioDTO("João", OUTRO_CPF_VALIDO, null, null, "porteiro", UUID.randomUUID(), "INQUILINO"), authAdmin);

        assertEquals("PORTARIA", resposta.perfil());
        assertNull(resposta.apartamentoId());
        assertNull(resposta.vinculo());
    }

    @Test
    void usuarioDeOutroCondominioNaoEhEncontrado() {
        var id = UUID.randomUUID();
        when(usuarioRepository.findByIdAndCondominioId(id, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(id, authAdmin));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.alterarStatus(id, false, authAdmin));
    }

    @Test
    void adminNaoDesativaASiMesmo() {
        when(usuarioRepository.findByIdAndCondominioId(admin.getId(), condominio.getId())).thenReturn(Optional.of(admin));

        assertThrows(IllegalArgumentException.class, () -> service.alterarStatus(admin.getId(), false, authAdmin));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void desativarEReativar() {
        var morador = usuario("MORADOR");
        when(usuarioRepository.findByIdAndCondominioId(morador.getId(), condominio.getId())).thenReturn(Optional.of(morador));
        when(usuarioRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        assertEquals("INATIVO", service.alterarStatus(morador.getId(), false, authAdmin).status());
        assertFalse(new UsuarioAutenticado(morador).isEnabled());
        assertEquals("ATIVO", service.alterarStatus(morador.getId(), true, authAdmin).status());
    }

    @Test
    void validacaoDeDigitosDoCpf() {
        assertTrue(UsuarioService.cpfValido("52998224725"));
        assertTrue(UsuarioService.cpfValido("15729185430"));
        assertFalse(UsuarioService.cpfValido("52998224724"));
        assertFalse(UsuarioService.cpfValido("00000000000"));
    }
}
