package br.com.condomais.interativo.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.model.Torre;
import br.com.condomais.condominio.repository.TorreRepository;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.dto.RegistroAvisoDTO;
import br.com.condomais.interativo.model.Aviso;
import br.com.condomais.interativo.repository.AvisoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvisoServiceTest {

    @Mock private AvisoRepository avisoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TorreRepository torreRepository;
    @InjectMocks private AvisoService service;

    private final LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private Condominio condominio;
    private Torre torreA;
    private Torre torreB;
    private Usuario admin;
    private Usuario morador;

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        torreA = torre("Torre A");
        torreB = torre("Torre B");
        admin = usuario("ADMIN", null);
        var unidade = new Apartamento();
        unidade.setId(UUID.randomUUID());
        unidade.setTorre(torreA);
        morador = usuario("MORADOR", unidade);
    }

    private Torre torre(String nome) {
        var torre = new Torre();
        torre.setId(UUID.randomUUID());
        torre.setNome(nome);
        torre.setCondominio(condominio);
        return torre;
    }

    private Usuario usuario(String perfil, Apartamento apartamento) {
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Fulano " + perfil);
        usuario.setPerfil(perfil);
        usuario.setCondominio(condominio);
        usuario.setApartamento(apartamento);
        return usuario;
    }

    private Aviso aviso(Torre torre, LocalDate validade) {
        var aviso = new Aviso();
        aviso.setId(UUID.randomUUID());
        aviso.setTitulo("Manutenção");
        aviso.setConteudo("Elevador parado");
        aviso.setPrioridade("NORMAL");
        aviso.setDataValidade(validade);
        aviso.setCreatedAt(LocalDateTime.now());
        aviso.setAutor(admin);
        aviso.setTorre(torre);
        aviso.setCondominio(condominio);
        return aviso;
    }

    private static UsuarioAutenticado auth(Usuario usuario) {
        return new UsuarioAutenticado(usuario);
    }

    // ---------------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------------

    @Test
    void adminVeTodosInclusiveVencidosEPodeFiltrar() {
        var vencido = aviso(null, hoje.minusDays(1));
        var ativo = aviso(torreB, null);
        when(avisoRepository.findByCondominioIdOrderByCreatedAtDesc(condominio.getId())).thenReturn(List.of(ativo, vencido));

        var todos = service.listar(false, auth(admin));
        var ativos = service.listar(true, auth(admin));

        assertEquals(2, todos.size());
        assertFalse(todos.get(1).ativo());
        assertEquals(List.of(ativo.getId()), ativos.stream().map(a -> a.id()).toList());
        assertEquals("Torre B", todos.get(0).torreNome());
        assertEquals("Fulano ADMIN", todos.get(0).autorNome());
    }

    @Test
    void moradorRecebeOsAtivosDaTorreDele() {
        when(usuarioRepository.findById(morador.getId())).thenReturn(Optional.of(morador));
        when(avisoRepository.buscarAvisosAtivosParaMorador(condominio.getId(), torreA.getId(), hoje)).thenReturn(List.of(aviso(torreA, null)));

        assertEquals(1, service.listar(false, auth(morador)).size());
        verify(avisoRepository, never()).findByCondominioIdOrderByCreatedAtDesc(any());
    }

    @Test
    void moradorSemTorreRecebeSoOsGerais() {
        var semTorre = usuario("MORADOR", new Apartamento());
        when(usuarioRepository.findById(semTorre.getId())).thenReturn(Optional.of(semTorre));
        when(avisoRepository.buscarAvisosGeraisAtivos(condominio.getId(), hoje)).thenReturn(List.of());

        service.listar(false, auth(semTorre));

        verify(avisoRepository).buscarAvisosGeraisAtivos(condominio.getId(), hoje);
        verify(avisoRepository, never()).buscarAvisosAtivosParaMorador(any(), any(), any());
    }

    @Test
    void moradorNaoAbreAvisoDeOutraTorreNemVencido() {
        var deOutraTorre = aviso(torreB, null);
        var vencido = aviso(null, hoje.minusDays(1));
        when(avisoRepository.findByIdAndCondominioId(deOutraTorre.getId(), condominio.getId())).thenReturn(Optional.of(deOutraTorre));
        when(avisoRepository.findByIdAndCondominioId(vencido.getId(), condominio.getId())).thenReturn(Optional.of(vencido));
        when(usuarioRepository.findById(morador.getId())).thenReturn(Optional.of(morador));

        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(deOutraTorre.getId(), auth(morador)));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(vencido.getId(), auth(morador)));
    }

    @Test
    void moradorAbreAvisoGeralEOdaTorreDele() {
        var geral = aviso(null, hoje);
        var daTorre = aviso(torreA, null);
        when(avisoRepository.findByIdAndCondominioId(geral.getId(), condominio.getId())).thenReturn(Optional.of(geral));
        when(avisoRepository.findByIdAndCondominioId(daTorre.getId(), condominio.getId())).thenReturn(Optional.of(daTorre));
        when(usuarioRepository.findById(morador.getId())).thenReturn(Optional.of(morador));

        assertTrue(service.buscar(geral.getId(), auth(morador)).ativo());
        assertEquals("Torre A", service.buscar(daTorre.getId(), auth(morador)).torreNome());
    }

    @Test
    void avisoDeOutroCondominioNaoEhEncontrado() {
        var id = UUID.randomUUID();
        when(avisoRepository.findByIdAndCondominioId(id, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(id, auth(admin)));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.excluir(id, auth(admin)));
    }

    // ---------------------------------------------------------------------
    // Publicação, edição e exclusão
    // ---------------------------------------------------------------------

    @Test
    void portariaPublicaComPrioridadePadraoNormal() {
        var portaria = usuario("Porteiro", null);
        when(usuarioRepository.findById(portaria.getId())).thenReturn(Optional.of(portaria));
        when(avisoRepository.save(any(Aviso.class))).thenAnswer(i -> i.getArgument(0));

        var resposta = service.publicar(new RegistroAvisoDTO(" Água ", "Falta d'água às 14h", null, hoje.plusDays(2), null), auth(portaria));

        assertEquals("Água", resposta.titulo());
        assertEquals("NORMAL", resposta.prioridade());
        assertNull(resposta.torreId());
        assertTrue(resposta.ativo());
    }

    @Test
    void publicacaoParaUmaTorreDoCondominio() {
        when(usuarioRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(torreRepository.findByIdAndCondominioId(torreA.getId(), condominio.getId())).thenReturn(Optional.of(torreA));
        when(avisoRepository.save(any(Aviso.class))).thenAnswer(i -> i.getArgument(0));

        var resposta = service.publicar(new RegistroAvisoDTO("Pintura", "Hall da torre", "urgente", null, torreA.getId()), auth(admin));

        assertEquals("URGENTE", resposta.prioridade());
        assertEquals("Torre A", resposta.torreNome());
    }

    @Test
    void torreDeOutroCondominioEhRecusada() {
        var deOutro = UUID.randomUUID();
        when(usuarioRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(torreRepository.findByIdAndCondominioId(deOutro, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.publicar(new RegistroAvisoDTO("T", "C", null, null, deOutro), auth(admin)));
        verify(avisoRepository, never()).save(any());
    }

    @Test
    void validacoesDaPublicacao() {
        when(usuarioRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThrows(IllegalArgumentException.class, () -> service.publicar(new RegistroAvisoDTO(" ", "C", null, null, null), auth(admin)));
        assertThrows(IllegalArgumentException.class, () -> service.publicar(new RegistroAvisoDTO("T", null, null, null, null), auth(admin)));
        assertThrows(IllegalArgumentException.class, () -> service.publicar(new RegistroAvisoDTO("T", "C", "ALTA", null, null), auth(admin)));
        assertThrows(IllegalArgumentException.class, () -> service.publicar(new RegistroAvisoDTO("T", "C", null, hoje.minusDays(1), null), auth(admin)));
        verify(avisoRepository, never()).save(any());
    }

    @Test
    void edicaoMantemAutorEDataETrocaATorre() {
        var aviso = aviso(torreA, null);
        var criadoEm = aviso.getCreatedAt();
        when(avisoRepository.findByIdAndCondominioId(aviso.getId(), condominio.getId())).thenReturn(Optional.of(aviso));
        when(avisoRepository.save(aviso)).thenReturn(aviso);

        var resposta = service.atualizar(aviso.getId(), new RegistroAvisoDTO("Novo título", "Novo texto", "URGENTE", null, null), auth(admin));

        assertEquals("Novo título", resposta.titulo());
        assertNull(resposta.torreId());
        assertEquals(criadoEm, resposta.criadoEm());
        assertEquals("Fulano ADMIN", resposta.autorNome());
    }

    @Test
    void adminExclui() {
        var aviso = aviso(null, null);
        when(avisoRepository.findByIdAndCondominioId(aviso.getId(), condominio.getId())).thenReturn(Optional.of(aviso));

        service.excluir(aviso.getId(), auth(admin));

        verify(avisoRepository).delete(aviso);
    }

    @Test
    void moradorNaoPublicaNemEditaNemExclui() {
        var id = UUID.randomUUID();

        assertThrows(SecurityException.class, () -> service.publicar(new RegistroAvisoDTO("T", "C", null, null, null), auth(morador)));
        assertThrows(SecurityException.class, () -> service.atualizar(id, new RegistroAvisoDTO("T", "C", null, null, null), auth(morador)));
        assertThrows(SecurityException.class, () -> service.excluir(id, auth(morador)));
        verify(avisoRepository, never()).save(any());
        verify(avisoRepository, never()).delete(any());
        verify(avisoRepository, never()).findByIdAndCondominioId(eq(id), any());
    }
}
