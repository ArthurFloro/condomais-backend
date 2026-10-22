package br.com.condomais.plataforma.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.repository.CondominioRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.plataforma.dto.CadastroCondominioDTO;
import br.com.condomais.plataforma.dto.DadosAdminDTO;
import br.com.condomais.plataforma.dto.DadosCondominioDTO;
import br.com.condomais.plataforma.dto.TrocaAdminDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class PlataformaServiceTest {

    // CPFs com dígitos verificadores válidos, gerados só para teste
    private static final String CPF_VALIDO = "529.982.247-25";

    @Mock private CondominioRepository condominioRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private PlataformaService service;

    private UsuarioAutenticado master;
    private Condominio condominio;

    @BeforeEach
    void setUp() {
        var usuarioMaster = new Usuario();
        usuarioMaster.setId(UUID.randomUUID());
        usuarioMaster.setPerfil("MASTER");
        master = new UsuarioAutenticado(usuarioMaster);

        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        condominio.setNome("Residencial Teste");
        condominio.setCnpj("12.345.678/0001-90");
        condominio.setStatus("ATIVO");
    }

    private Usuario usuario(String perfil) {
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Fulano");
        usuario.setPerfil(perfil);
        usuario.setStatus("ATIVO");
        usuario.setCondominio(condominio);
        return usuario;
    }

    private CadastroCondominioDTO cadastro(String cnpj, String cpf) {
        return new CadastroCondominioDTO(
                new DadosCondominioDTO("Residencial Novo", cnpj),
                new DadosAdminDTO("Maria Souza", cpf, "maria@exemplo.com", null));
    }

    @Test
    void cadastraCondominioAtivoComAdminSemSenhaECpfFormatado() {
        when(condominioRepository.findByCnpj("98.765.432/0001-10")).thenReturn(Optional.empty());
        when(condominioRepository.save(any(Condominio.class))).thenAnswer(i -> {
            Condominio c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        var resposta = service.cadastrar(cadastro("98.765.432/0001-10", "52998224725"), master);

        var admin = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(admin.capture());
        assertEquals("ADMIN", admin.getValue().getPerfil());
        assertEquals(CPF_VALIDO, admin.getValue().getCpf());
        assertNull(admin.getValue().getSenha());
        assertEquals(resposta.id(), admin.getValue().getCondominio().getId());
        assertEquals("ATIVO", resposta.status());
    }

    @Test
    void cnpjRepetidoNoCadastroEhConflito() {
        when(condominioRepository.findByCnpj("12.345.678/0001-90")).thenReturn(Optional.of(condominio));

        assertThrows(ConflitoException.class, () -> service.cadastrar(cadastro("12.345.678/0001-90", CPF_VALIDO), master));
        verify(condominioRepository, never()).save(any());
    }

    @Test
    void cpfDoAdminJaCadastradoEhConflito() {
        when(condominioRepository.findByCnpj(any())).thenReturn(Optional.empty());
        when(condominioRepository.save(any(Condominio.class))).thenAnswer(i -> i.getArgument(0));
        when(usuarioRepository.findByCpf(CPF_VALIDO)).thenReturn(Optional.of(usuario("MORADOR")));

        assertThrows(ConflitoException.class, () -> service.cadastrar(cadastro("98.765.432/0001-10", CPF_VALIDO), master));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void edicaoNaoAceitaCnpjDeOutroCondominio() {
        when(condominioRepository.findById(condominio.getId())).thenReturn(Optional.of(condominio));
        when(condominioRepository.existsByCnpjAndIdNot("11.111.111/0001-11", condominio.getId())).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> service.atualizar(condominio.getId(), new DadosCondominioDTO("Outro nome", "11.111.111/0001-11"), master));
        verify(condominioRepository, never()).save(any());
    }

    @Test
    void desativarMarcaOCondominioComoInativo() {
        when(condominioRepository.findById(condominio.getId())).thenReturn(Optional.of(condominio));
        when(condominioRepository.save(condominio)).thenReturn(condominio);

        var resposta = service.alterarStatus(condominio.getId(), false, master);

        assertEquals("INATIVO", condominio.getStatus());
        assertFalse(condominio.isAtivo());
        assertEquals("INATIVO", resposta.status());
    }

    @Test
    void trocaPorAdminNovoDesativaOAnterior() {
        var anterior = usuario("Administrador");
        var morador = usuario("MORADOR");
        when(condominioRepository.findById(condominio.getId())).thenReturn(Optional.of(condominio));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(usuarioRepository.findByCondominioId(condominio.getId())).thenReturn(List.of(anterior, morador));

        service.trocarAdmin(condominio.getId(), new TrocaAdminDTO(null, "Novo Admin", CPF_VALIDO, null, null), master);

        assertEquals("INATIVO", anterior.getStatus());
        assertEquals("ATIVO", morador.getStatus());
    }

    @Test
    void promoverMoradorTiraUnidadeEVinculoEDesativaAdminAnterior() {
        var anterior = usuario("ADMIN");
        var morador = usuario("MORADOR");
        morador.setApartamento(new Apartamento());
        morador.setVinculo("PROPRIETARIO");
        when(condominioRepository.findById(condominio.getId())).thenReturn(Optional.of(condominio));
        when(usuarioRepository.findByIdAndCondominioId(morador.getId(), condominio.getId())).thenReturn(Optional.of(morador));
        when(usuarioRepository.save(morador)).thenReturn(morador);
        when(usuarioRepository.findByCondominioId(condominio.getId())).thenReturn(List.of(anterior, morador));

        var resposta = service.trocarAdmin(condominio.getId(), new TrocaAdminDTO(morador.getId(), null, null, null, null), master);

        assertEquals("ADMIN", morador.getPerfil());
        assertNull(morador.getApartamento());
        assertNull(morador.getVinculo());
        assertEquals("ATIVO", morador.getStatus());
        assertEquals("INATIVO", anterior.getStatus());
        // Os dois aparecem como administradores, mas só o promovido continua ativo
        var ativos = resposta.administradores().stream().filter(a -> "ATIVO".equals(a.status())).map(a -> a.id()).toList();
        assertEquals(List.of(morador.getId()), ativos);
    }

    @Test
    void naoPromoveUsuarioDeOutroCondominio() {
        var deOutro = UUID.randomUUID();
        when(condominioRepository.findById(condominio.getId())).thenReturn(Optional.of(condominio));
        when(usuarioRepository.findByIdAndCondominioId(deOutro, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.trocarAdmin(condominio.getId(), new TrocaAdminDTO(deOutro, null, null, null, null), master));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void listaTrazSoOsAdministradoresDeCadaCondominio() {
        var admin = usuario("ADMIN");
        when(condominioRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(condominio));
        when(usuarioRepository.findByCondominioIdIn(List.of(condominio.getId()))).thenReturn(List.of(admin, usuario("MORADOR")));

        var lista = service.listar(master);

        assertEquals(1, lista.size());
        assertEquals(List.of(admin.getId()), lista.get(0).administradores().stream().map(a -> a.id()).toList());
    }

    @Test
    void soOMasterUsaAPlataforma() {
        var admin = new UsuarioAutenticado(usuario("ADMIN"));

        assertThrows(SecurityException.class, () -> service.listar(admin));
        assertThrows(SecurityException.class, () -> service.cadastrar(cadastro("98.765.432/0001-10", CPF_VALIDO), admin));
        verifyNoInteractions(condominioRepository, usuarioRepository);
    }
}
