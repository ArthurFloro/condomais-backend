package br.com.condomais.portaria.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioApartamentoRepository;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.model.Torre;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.portaria.dto.RegistroEncomendaDTO;
import br.com.condomais.portaria.model.Encomenda;
import br.com.condomais.portaria.repository.EncomendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncomendaServiceTest {

    @Mock private EncomendaRepository encomendaRepository;
    @Mock private ApartamentoRepository apartamentoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioApartamentoRepository usuarioApartamentoRepository;
    @InjectMocks private EncomendaService service;

    private Condominio condominio;
    private Apartamento unidade101;
    private Apartamento unidade102;
    private Usuario porteiro;
    private Usuario morador;

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        var torre = new Torre();
        torre.setNome("Torre A");
        unidade101 = unidade("101", torre);
        unidade102 = unidade("102", torre);
        porteiro = usuario("PORTARIA", "Porteiro", null);
        morador = usuario("MORADOR", "Morador 101", unidade101);
    }

    private Apartamento unidade(String numero, Torre torre) {
        var apartamento = new Apartamento();
        apartamento.setId(UUID.randomUUID());
        apartamento.setNumero(numero);
        apartamento.setTorre(torre);
        apartamento.setCondominio(condominio);
        return apartamento;
    }

    private Usuario usuario(String perfil, String nome, Apartamento apartamento) {
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome(nome);
        usuario.setPerfil(perfil);
        usuario.setCondominio(condominio);
        usuario.setApartamento(apartamento);
        return usuario;
    }

    private Encomenda encomenda(Apartamento apartamento, String status) {
        var encomenda = new Encomenda();
        encomenda.setId(UUID.randomUUID());
        encomenda.setCodigoIdentificacao("BR123");
        encomenda.setStatus(status);
        encomenda.setDataHoraRecebimento(LocalDateTime.now());
        encomenda.setApartamento(apartamento);
        encomenda.setCondominio(condominio);
        encomenda.setPorteiroRecebimento(porteiro);
        return encomenda;
    }

    private static UsuarioAutenticado auth(Usuario usuario) {
        return new UsuarioAutenticado(usuario);
    }

    // ---------------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------------

    @Test
    void portariaListaTodasDoCondominio() {
        when(encomendaRepository.findByCondominioIdOrderByDataHoraRecebimentoDesc(condominio.getId()))
                .thenReturn(List.of(encomenda(unidade101, "AGUARDANDO RETIRADA"), encomenda(unidade102, "RETIRADA")));

        var lista = service.listar(null, null, auth(porteiro));

        assertEquals(2, lista.size());
        assertEquals("101", lista.get(0).apartamentoNumero());
        assertEquals("Torre A", lista.get(0).torreNome());
        assertEquals("Porteiro", lista.get(0).porteiroRecebimentoNome());
    }

    @Test
    void filtroDeStatusAceitaUnderline() {
        when(encomendaRepository.findByCondominioIdAndStatusOrderByDataHoraRecebimentoDesc(condominio.getId(), "AGUARDANDO RETIRADA"))
                .thenReturn(List.of());

        service.listar("aguardando_retirada", null, auth(porteiro));

        verify(encomendaRepository).findByCondominioIdAndStatusOrderByDataHoraRecebimentoDesc(condominio.getId(), "AGUARDANDO RETIRADA");
    }

    @Test
    void statusInvalidoEhRecusado() {
        assertThrows(IllegalArgumentException.class, () -> service.listar("PERDIDA", null, auth(porteiro)));
    }

    @Test
    void moradorVeSoAsDaPropriaUnidadeMesmoPedindoOutra() {
        when(usuarioRepository.findById(morador.getId())).thenReturn(Optional.of(morador));
        when(encomendaRepository.findByCondominioIdAndApartamentoIdOrderByDataHoraRecebimentoDesc(condominio.getId(), unidade101.getId()))
                .thenReturn(List.of(encomenda(unidade101, "AGUARDANDO RETIRADA")));

        var lista = service.listar(null, unidade102.getId(), auth(morador));

        assertEquals(1, lista.size());
        assertEquals("101", lista.get(0).apartamentoNumero());
        verify(encomendaRepository, never()).findByCondominioIdAndApartamentoIdOrderByDataHoraRecebimentoDesc(condominio.getId(), unidade102.getId());
    }

    @Test
    void moradorSemUnidadeRecebeListaVazia() {
        var semUnidade = usuario("MORADOR", "Sem unidade", null);
        when(usuarioRepository.findById(semUnidade.getId())).thenReturn(Optional.of(semUnidade));

        assertEquals(List.of(), service.listar(null, null, auth(semUnidade)));
        verifyNoInteractions(encomendaRepository);
    }

    @Test
    void moradorNaoConsultaEncomendaDeOutraUnidade() {
        var deOutraUnidade = encomenda(unidade102, "AGUARDANDO RETIRADA");
        when(encomendaRepository.findByIdAndCondominioId(deOutraUnidade.getId(), condominio.getId())).thenReturn(Optional.of(deOutraUnidade));
        when(usuarioRepository.findById(morador.getId())).thenReturn(Optional.of(morador));

        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(deOutraUnidade.getId(), auth(morador)));
    }

    @Test
    void encomendaDeOutroCondominioNaoEhEncontrada() {
        var id = UUID.randomUUID();
        when(encomendaRepository.findByIdAndCondominioId(id, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(id, auth(porteiro)));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.registrarRetirada(id, auth(porteiro)));
    }

    // ---------------------------------------------------------------------
    // Registro
    // ---------------------------------------------------------------------

    @Test
    void portariaRegistraRecebimento() {
        when(apartamentoRepository.findByIdAndCondominioId(unidade101.getId(), condominio.getId())).thenReturn(Optional.of(unidade101));
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        when(encomendaRepository.save(any(Encomenda.class))).thenAnswer(i -> i.getArgument(0));

        var resposta = service.registrarRecebimento(new RegistroEncomendaDTO(unidade101.getId(), " BR123 ", "caixa"), auth(porteiro));

        assertEquals("AGUARDANDO RETIRADA", resposta.status());
        assertEquals("BR123", resposta.codigoIdentificacao());
        assertEquals("Porteiro", resposta.porteiroRecebimentoNome());
    }

    @Test
    void recebimentoSemCodigoEhRecusado() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarRecebimento(new RegistroEncomendaDTO(unidade101.getId(), " ", null), auth(porteiro)));
        verify(encomendaRepository, never()).save(any());
    }

    @Test
    void moradorNaoRegistraNemDaBaixa() {
        assertThrows(SecurityException.class,
                () -> service.registrarRecebimento(new RegistroEncomendaDTO(unidade101.getId(), "BR1", null), auth(morador)));
        assertThrows(SecurityException.class, () -> service.registrarRetirada(UUID.randomUUID(), auth(morador)));
        verifyNoInteractions(encomendaRepository);
    }

    @Test
    void adminComPerfilAntigoTambemRegistra() {
        var admin = usuario("Administrador", "Admin", null);
        var aberta = encomenda(unidade101, "AGUARDANDO RETIRADA");
        when(encomendaRepository.findByIdAndCondominioId(aberta.getId(), condominio.getId())).thenReturn(Optional.of(aberta));
        when(usuarioRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(encomendaRepository.save(aberta)).thenReturn(aberta);

        var resposta = service.registrarRetirada(aberta.getId(), auth(admin));

        assertEquals("RETIRADA", resposta.status());
        assertEquals("Admin", resposta.porteiroRetiradaNome());
        assertNotNull(resposta.dataHoraRetirada());
    }

    @Test
    void retiradaDuplaEhConflito() {
        var retirada = encomenda(unidade101, "RETIRADA");
        when(encomendaRepository.findByIdAndCondominioId(retirada.getId(), condominio.getId())).thenReturn(Optional.of(retirada));

        assertThrows(ConflitoException.class, () -> service.registrarRetirada(retirada.getId(), auth(porteiro)));
        verify(encomendaRepository, never()).save(any());
    }
}
