package br.com.condomais.condominio.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.condominio.dto.ApartamentoDTO;
import br.com.condomais.condominio.dto.TorreDTO;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.AreaComum;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.model.Torre;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.condominio.repository.AreaComumRepository;
import br.com.condomais.condominio.repository.CondominioRepository;
import br.com.condomais.condominio.repository.TorreRepository;
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
class CondominioServiceTest {

    @Mock private CondominioRepository condominioRepository;
    @Mock private TorreRepository torreRepository;
    @Mock private ApartamentoRepository apartamentoRepository;
    @Mock private AreaComumRepository areaComumRepository;
    @InjectMocks private CondominioService service;

    private Condominio condominio;
    private UsuarioAutenticado admin;
    private UsuarioAutenticado morador;

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        admin = autenticado("ADMIN");
        morador = autenticado("MORADOR");
    }

    private UsuarioAutenticado autenticado(String perfil) {
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setPerfil(perfil);
        usuario.setCondominio(condominio);
        return new UsuarioAutenticado(usuario);
    }

    private Torre torre() {
        var torre = new Torre();
        torre.setId(UUID.randomUUID());
        torre.setNome("Torre A");
        torre.setCondominio(condominio);
        return torre;
    }

    // ---------------------------------------------------------------------
    // Leitura: sempre do condomínio do token
    // ---------------------------------------------------------------------

    @Test
    void listaTorresSoDoCondominioDoToken() {
        when(torreRepository.findByCondominioId(condominio.getId())).thenReturn(List.of(torre()));

        assertEquals(1, service.listarTorres(morador).size());
        verify(torreRepository, never()).findAll();
    }

    @Test
    void filtroPorTorreDeApartamentosTambemFicaNoCondominioDoToken() {
        var torreId = UUID.randomUUID();
        when(apartamentoRepository.findByTorreIdAndCondominioId(torreId, condominio.getId())).thenReturn(List.of());

        service.listarApartamentos(torreId, morador);

        verify(apartamentoRepository, never()).findByTorreId(any());
        verify(apartamentoRepository, never()).findAll();
    }

    @Test
    void listaAreasComunsSoDoCondominioDoTokenParaQualquerPerfil() {
        var area = new AreaComum();
        area.setId(UUID.randomUUID());
        area.setCondominio(condominio);
        when(areaComumRepository.findByCondominioIdAndAtivoTrue(condominio.getId())).thenReturn(List.of(area));

        assertEquals(1, service.listarAreasComuns(true, morador).size());
        verify(areaComumRepository, never()).findByAtivoTrue();
    }

    @Test
    void listaDeCondominiosTemSoODoToken() {
        when(condominioRepository.findById(condominio.getId())).thenReturn(Optional.of(condominio));

        assertEquals(List.of(condominio), service.listarCondominios(morador));
        verify(condominioRepository, never()).findAll();
    }

    @Test
    void condominioDeOutroTenantRespondeNaoEncontrado() {
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscarCondominio(UUID.randomUUID(), admin));
    }

    @Test
    void torreDeOutroCondominioRespondeNaoEncontrada() {
        var id = UUID.randomUUID();
        when(torreRepository.findByIdAndCondominioId(id, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscarTorre(id, morador));
        verify(torreRepository, never()).findById(any());
    }

    @Test
    void apartamentoDeOutroCondominioNaoPodeSerExcluido() {
        var id = UUID.randomUUID();
        when(apartamentoRepository.findByIdAndCondominioId(id, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.excluirApartamento(id, admin));
        verify(apartamentoRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------------
    // Escrita: condomínio vem do token, e só ADMIN
    // ---------------------------------------------------------------------

    @Test
    void cadastroDeTorreIgnoraCondominioDoCorpo() {
        when(condominioRepository.findById(condominio.getId())).thenReturn(Optional.of(condominio));
        when(torreRepository.save(any(Torre.class))).thenAnswer(i -> i.getArgument(0));

        var torre = service.cadastrarTorre(new TorreDTO("Torre B", UUID.randomUUID()), admin);

        assertSame(condominio, torre.getCondominio());
    }

    @Test
    void apartamentoNaoRecebeTorreDeOutroCondominio() {
        var torreDeOutro = UUID.randomUUID();
        when(condominioRepository.findById(condominio.getId())).thenReturn(Optional.of(condominio));
        when(torreRepository.findByIdAndCondominioId(torreDeOutro, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.cadastrarApartamento(new ApartamentoDTO("101", "ocupado", torreDeOutro, null), admin));
        verify(apartamentoRepository, never()).save(any());
    }

    @Test
    void edicaoDeApartamentoNaoTrocaParaTorreDeOutroCondominio() {
        var apartamento = new Apartamento();
        apartamento.setId(UUID.randomUUID());
        apartamento.setCondominio(condominio);
        var torreDeOutro = UUID.randomUUID();
        when(apartamentoRepository.findByIdAndCondominioId(apartamento.getId(), condominio.getId())).thenReturn(Optional.of(apartamento));
        when(torreRepository.findByIdAndCondominioId(torreDeOutro, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.atualizarApartamento(apartamento.getId(), new ApartamentoDTO("102", "vago", torreDeOutro, null), admin));
        verify(apartamentoRepository, never()).save(any());
    }

    @Test
    void moradorNaoAlteraAEstrutura() {
        var id = UUID.randomUUID();

        assertThrows(SecurityException.class, () -> service.cadastrarTorre(new TorreDTO("Torre B", null), morador));
        assertThrows(SecurityException.class, () -> service.excluirApartamento(id, morador));
        assertThrows(SecurityException.class, () -> service.alterarStatusAreaComum(id, false, morador));
        verifyNoInteractions(torreRepository, apartamentoRepository, areaComumRepository);
    }

    @Test
    void portariaNaoAlteraAEstrutura() {
        assertThrows(SecurityException.class,
                () -> service.excluirTorre(UUID.randomUUID(), autenticado("Portaria")));
        verifyNoInteractions(torreRepository);
    }

    @Test
    void adminComPerfilLegadoAindaAltera() {
        when(condominioRepository.findById(condominio.getId())).thenReturn(Optional.of(condominio));
        when(torreRepository.save(any(Torre.class))).thenAnswer(i -> i.getArgument(0));

        assertNotNull(service.cadastrarTorre(new TorreDTO("Torre C", null), autenticado("Administrador")));
    }

    // ---------------------------------------------------------------------
    // Unicidade no cadastro manual: 409 legível antes do índice único do banco
    // ---------------------------------------------------------------------

    @Test
    void torreComNomeRepetidoNoCondominioEhConflito() {
        when(torreRepository.existsByCondominioIdAndNomeIgnoreCase(condominio.getId(), "torre a")).thenReturn(true);

        assertThrows(ConflitoException.class, () -> service.cadastrarTorre(new TorreDTO("torre a", null), admin));
        verify(torreRepository, never()).save(any());
    }

    @Test
    void renomearTorreParaNomeDeOutraEhConflito() {
        var torre = torre();
        when(torreRepository.findByIdAndCondominioId(torre.getId(), condominio.getId())).thenReturn(Optional.of(torre));
        when(torreRepository.existsByCondominioIdAndNomeIgnoreCaseAndIdNot(condominio.getId(), "Torre B", torre.getId())).thenReturn(true);

        assertThrows(ConflitoException.class, () -> service.atualizarTorre(torre.getId(), new TorreDTO("Torre B", null), admin));
        verify(torreRepository, never()).save(any());
    }

    @Test
    void unidadeComNumeroRepetidoNaTorreEhConflito() {
        var torre = torre();
        when(condominioRepository.findById(condominio.getId())).thenReturn(Optional.of(condominio));
        when(torreRepository.findByIdAndCondominioId(torre.getId(), condominio.getId())).thenReturn(Optional.of(torre));
        when(apartamentoRepository.existsByTorreIdAndNumero(torre.getId(), "101")).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> service.cadastrarApartamento(new ApartamentoDTO("101", "LIVRE", torre.getId(), null), admin));
        verify(apartamentoRepository, never()).save(any());
    }

    @Test
    void unidadeSemTorreComNumeroRepetidoEhConflito() {
        when(condominioRepository.findById(condominio.getId())).thenReturn(Optional.of(condominio));
        when(apartamentoRepository.existsByCondominioIdAndTorreIsNullAndNumero(condominio.getId(), "101")).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> service.cadastrarApartamento(new ApartamentoDTO("101", "LIVRE", null, null), admin));
    }
}
