package br.com.condomais.atendimento.service;

import br.com.condomais.atendimento.dto.CategoriaChamadoDTO;
import br.com.condomais.atendimento.model.CategoriaChamado;
import br.com.condomais.atendimento.repository.CategoriaChamadoRepository;
import br.com.condomais.atendimento.repository.ChamadoRepository;
import br.com.condomais.auth.model.Usuario;
import br.com.condomais.condominio.model.Condominio;
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
class CategoriaChamadoServiceTest {

    @Mock private CategoriaChamadoRepository categoriaRepository;
    @Mock private ChamadoRepository chamadoRepository;
    @Mock private CondominioRepository condominioRepository;
    @InjectMocks private CategoriaChamadoService service;

    private Condominio condominio;
    private UsuarioAutenticado admin;
    private UsuarioAutenticado morador;

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        admin = auth("ADMIN");
        morador = auth("MORADOR");
    }

    private UsuarioAutenticado auth(String perfil) {
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setPerfil(perfil);
        usuario.setCondominio(condominio);
        return new UsuarioAutenticado(usuario);
    }

    private CategoriaChamado categoria(String nome) {
        var categoria = new CategoriaChamado();
        categoria.setId(UUID.randomUUID());
        categoria.setNome(nome);
        categoria.setCondominio(condominio);
        return categoria;
    }

    @Test
    void qualquerPerfilListaEmOrdemAlfabetica() {
        when(condominioRepository.getReferenceById(condominio.getId())).thenReturn(condominio);
        when(categoriaRepository.findFirstByCondominioIdAndNomeIgnoreCase(condominio.getId(), "Outros")).thenReturn(Optional.of(categoria("Outros")));
        when(categoriaRepository.findByCondominioIdOrderByNomeAsc(condominio.getId())).thenReturn(List.of(categoria("Elétrica"), categoria("Outros")));

        assertEquals(List.of("Elétrica", "Outros"), service.listar(morador).stream().map(c -> c.nome()).toList());
        verify(categoriaRepository, never()).save(any());
    }

    @Test
    void listarCriaOutrosQuandoFalta() {
        when(condominioRepository.getReferenceById(condominio.getId())).thenReturn(condominio);
        when(categoriaRepository.findFirstByCondominioIdAndNomeIgnoreCase(condominio.getId(), "Outros")).thenReturn(Optional.empty());
        when(categoriaRepository.save(any(CategoriaChamado.class))).thenAnswer(i -> i.getArgument(0));

        service.listar(morador);

        var criada = org.mockito.ArgumentCaptor.forClass(CategoriaChamado.class);
        verify(categoriaRepository).save(criada.capture());
        assertEquals("Outros", criada.getValue().getNome());
        assertSame(condominio, criada.getValue().getCondominio());
    }

    @Test
    void outrosNaoPodeSerRenomeadaNemExcluida() {
        var outros = categoria("Outros");
        when(categoriaRepository.findByIdAndCondominioId(outros.getId(), condominio.getId())).thenReturn(Optional.of(outros));

        assertThrows(ConflitoException.class, () -> service.renomear(outros.getId(), new CategoriaChamadoDTO("Diversos"), admin));
        assertThrows(ConflitoException.class, () -> service.excluir(outros.getId(), admin));
        verify(categoriaRepository, never()).save(any());
        verify(categoriaRepository, never()).delete(any());
    }

    @Test
    void adminCadastra() {
        when(condominioRepository.getReferenceById(condominio.getId())).thenReturn(condominio);
        when(categoriaRepository.save(any(CategoriaChamado.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("Hidráulica", service.cadastrar(new CategoriaChamadoDTO(" Hidráulica "), admin).nome());
    }

    @Test
    void nomeRepetidoOuVazioEhRecusado() {
        when(categoriaRepository.existsByCondominioIdAndNomeIgnoreCase(condominio.getId(), "elétrica")).thenReturn(true);

        assertThrows(ConflitoException.class, () -> service.cadastrar(new CategoriaChamadoDTO("elétrica"), admin));
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(new CategoriaChamadoDTO(" "), admin));
        verify(categoriaRepository, never()).save(any());
    }

    @Test
    void renomearParaONomeDeOutraEhConflito() {
        var categoria = categoria("Elétrica");
        when(categoriaRepository.findByIdAndCondominioId(categoria.getId(), condominio.getId())).thenReturn(Optional.of(categoria));
        when(categoriaRepository.existsByCondominioIdAndNomeIgnoreCaseAndIdNot(condominio.getId(), "Limpeza", categoria.getId())).thenReturn(true);

        assertThrows(ConflitoException.class, () -> service.renomear(categoria.getId(), new CategoriaChamadoDTO("Limpeza"), admin));
    }

    @Test
    void naoExcluiCategoriaEmUso() {
        var categoria = categoria("Elétrica");
        when(categoriaRepository.findByIdAndCondominioId(categoria.getId(), condominio.getId())).thenReturn(Optional.of(categoria));
        when(chamadoRepository.existsByCategoriaId(categoria.getId())).thenReturn(true);

        assertThrows(ConflitoException.class, () -> service.excluir(categoria.getId(), admin));
        verify(categoriaRepository, never()).delete(any());
    }

    @Test
    void excluiCategoriaSemChamados() {
        var categoria = categoria("Antiga");
        when(categoriaRepository.findByIdAndCondominioId(categoria.getId(), condominio.getId())).thenReturn(Optional.of(categoria));

        service.excluir(categoria.getId(), admin);

        verify(categoriaRepository).delete(categoria);
    }

    @Test
    void categoriaDeOutroCondominioNaoEhEncontrada() {
        var id = UUID.randomUUID();
        when(categoriaRepository.findByIdAndCondominioId(id, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.excluir(id, admin));
    }

    @Test
    void soAdminGerencia() {
        assertThrows(SecurityException.class, () -> service.cadastrar(new CategoriaChamadoDTO("X"), morador));
        assertThrows(SecurityException.class, () -> service.renomear(UUID.randomUUID(), new CategoriaChamadoDTO("X"), auth("PORTARIA")));
        assertThrows(SecurityException.class, () -> service.excluir(UUID.randomUUID(), morador));
        verifyNoInteractions(categoriaRepository);
    }
}
