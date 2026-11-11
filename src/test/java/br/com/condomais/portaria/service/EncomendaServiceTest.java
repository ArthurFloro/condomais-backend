package br.com.condomais.portaria.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.model.Torre;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.portaria.dto.RegistroEncomendaDTO;
import br.com.condomais.portaria.dto.RetiradaEncomendaDTO;
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

    private static RetiradaEncomendaDTO retirada() {
        return new RetiradaEncomendaDTO("Carlos Souza", null, null);
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

        var lista = service.listar(null, null, null, false, auth(porteiro));

        assertEquals(2, lista.size());
        assertEquals("101", lista.get(0).apartamentoNumero());
        assertEquals("Torre A", lista.get(0).torreNome());
        assertEquals("Porteiro", lista.get(0).porteiroRecebimentoNome());
    }

    @Test
    void filtroDeStatusAceitaUnderline() {
        when(encomendaRepository.findByCondominioIdAndStatusOrderByDataHoraRecebimentoDesc(condominio.getId(), "AGUARDANDO RETIRADA"))
                .thenReturn(List.of());

        service.listar("aguardando_retirada", null, null, false, auth(porteiro));

        verify(encomendaRepository).findByCondominioIdAndStatusOrderByDataHoraRecebimentoDesc(condominio.getId(), "AGUARDANDO RETIRADA");
    }

    @Test
    void statusInvalidoEhRecusado() {
        assertThrows(IllegalArgumentException.class, () -> service.listar("PERDIDA", null, null, false, auth(porteiro)));
    }

    @Test
    void moradorVeSoAsDaPropriaUnidadeMesmoPedindoOutra() {
        when(usuarioRepository.findById(morador.getId())).thenReturn(Optional.of(morador));
        when(encomendaRepository.findByCondominioIdAndApartamentoIdOrderByDataHoraRecebimentoDesc(condominio.getId(), unidade101.getId()))
                .thenReturn(List.of(encomenda(unidade101, "AGUARDANDO RETIRADA")));

        var lista = service.listar(null, unidade102.getId(), null, false, auth(morador));

        assertEquals(1, lista.size());
        assertEquals("101", lista.get(0).apartamentoNumero());
        verify(encomendaRepository, never()).findByCondominioIdAndApartamentoIdOrderByDataHoraRecebimentoDesc(condominio.getId(), unidade102.getId());
    }

    @Test
    void moradorSemUnidadeRecebeListaVazia() {
        var semUnidade = usuario("MORADOR", "Sem unidade", null);
        when(usuarioRepository.findById(semUnidade.getId())).thenReturn(Optional.of(semUnidade));

        assertEquals(List.of(), service.listar(null, null, null, false, auth(semUnidade)));
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
        assertThrows(RecursoNaoEncontradoException.class, () -> service.registrarRetirada(id, retirada(), auth(porteiro)));
    }

    // ---------------------------------------------------------------------
    // Registro
    // ---------------------------------------------------------------------

    @Test
    void portariaRegistraRecebimento() {
        when(apartamentoRepository.findByIdAndCondominioId(unidade101.getId(), condominio.getId())).thenReturn(Optional.of(unidade101));
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        when(usuarioRepository.listarMoradoresAtivosDaUnidade(unidade101.getId())).thenReturn(List.of(morador));
        when(encomendaRepository.save(any(Encomenda.class))).thenAnswer(i -> i.getArgument(0));

        var resposta = service.registrarRecebimento(new RegistroEncomendaDTO(unidade101.getId(), " BR123 ", "caixa"), auth(porteiro));

        assertEquals("AGUARDANDO RETIRADA", resposta.status());
        assertEquals("BR123", resposta.codigoIdentificacao());
        assertEquals("Porteiro", resposta.porteiroRecebimentoNome());
        // O destinatário é o morador cadastrado na unidade (Usuario.apartamento)
        assertEquals("Morador 101", resposta.moradorNome());
        // Horário do condomínio, não do servidor
        var agoraEmSaoPaulo = LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"));
        assertTrue(java.time.Duration.between(resposta.dataHoraRecebimento(), agoraEmSaoPaulo).abs().toMinutes() < 2);
    }

    @Test
    void unidadeSemMoradorRegistraSemDestinatario() {
        when(apartamentoRepository.findByIdAndCondominioId(unidade102.getId(), condominio.getId())).thenReturn(Optional.of(unidade102));
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        when(usuarioRepository.listarMoradoresAtivosDaUnidade(unidade102.getId())).thenReturn(List.of());
        when(encomendaRepository.save(any(Encomenda.class))).thenAnswer(i -> i.getArgument(0));

        var resposta = service.registrarRecebimento(new RegistroEncomendaDTO(unidade102.getId(), "BR9", null), auth(porteiro));

        assertNull(resposta.moradorNome());
        assertEquals("102", resposta.apartamentoNumero());
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
        assertThrows(SecurityException.class, () -> service.registrarRetirada(UUID.randomUUID(), retirada(), auth(morador)));
        verifyNoInteractions(encomendaRepository);
    }

    @Test
    void adminSoConsultaNaoRegistraRecebimentoNemRetirada() {
        // Também com o valor antigo "Administrador" no banco (CONDO-12: consulta, mas não executa)
        for (var perfil : List.of("ADMIN", "Administrador")) {
            var admin = usuario(perfil, "Admin", null);
            assertThrows(SecurityException.class,
                    () -> service.registrarRecebimento(new RegistroEncomendaDTO(unidade101.getId(), "BR1", null), auth(admin)));
            assertThrows(SecurityException.class, () -> service.registrarRetirada(UUID.randomUUID(), retirada(), auth(admin)));
        }
        verifyNoInteractions(encomendaRepository);
    }

    @Test
    void porteiroComPerfilAntigoTambemRegistra() {
        var porteiro = usuario("Porteiro", "Seu José", null);
        var aberta = encomenda(unidade101, "AGUARDANDO RETIRADA");
        when(encomendaRepository.findByIdAndCondominioId(aberta.getId(), condominio.getId())).thenReturn(Optional.of(aberta));
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        when(encomendaRepository.save(aberta)).thenReturn(aberta);

        var resposta = service.registrarRetirada(aberta.getId(), retirada(), auth(porteiro));

        assertEquals("RETIRADA", resposta.status());
        assertEquals("Seu José", resposta.porteiroRetiradaNome());
        assertNotNull(resposta.dataHoraRetirada());
    }

    @Test
    void retiradaDuplaEhConflito() {
        var retirada = encomenda(unidade101, "RETIRADA");
        when(encomendaRepository.findByIdAndCondominioId(retirada.getId(), condominio.getId())).thenReturn(Optional.of(retirada));

        assertThrows(ConflitoException.class, () -> service.registrarRetirada(retirada.getId(), retirada(), auth(porteiro)));
        verify(encomendaRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Sem destinatário identificado e dados do retirante
    // ---------------------------------------------------------------------

    @Test
    void portariaRecebeSemUnidadeESemDestinatario() {
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        when(encomendaRepository.save(any(Encomenda.class))).thenAnswer(i -> i.getArgument(0));

        var resposta = service.registrarRecebimento(new RegistroEncomendaDTO(null, " BR999XX ", "Caixa marrom, nome Maria"), auth(porteiro));

        assertTrue(resposta.semDestinatario());
        assertNull(resposta.apartamentoId());
        assertNull(resposta.moradorNome());
        assertEquals("BR999XX", resposta.codigoIdentificacao());
        verifyNoInteractions(apartamentoRepository);
    }

    @Test
    void localizaPeloCodigoESoAsSemDestinatario() {
        var semUnidade = encomenda(null, "AGUARDANDO RETIRADA");
        semUnidade.setCodigoIdentificacao("BR 555 666");
        var daUnidade = encomenda(unidade101, "AGUARDANDO RETIRADA");
        when(encomendaRepository.findByCondominioIdOrderByDataHoraRecebimentoDesc(condominio.getId())).thenReturn(List.of(semUnidade, daUnidade));

        assertEquals(List.of(semUnidade.getId()), service.listar(null, null, "br555", false, auth(porteiro)).stream().map(e -> e.id()).toList());
        assertEquals(List.of(semUnidade.getId()), service.listar(null, null, null, true, auth(porteiro)).stream().map(e -> e.id()).toList());
    }

    @Test
    void moradorNaoVeEncomendaSemDestinatario() {
        var semUnidade = encomenda(null, "AGUARDANDO RETIRADA");
        var morador = usuario("MORADOR", "Ana", unidade101);
        when(encomendaRepository.findByIdAndCondominioId(semUnidade.getId(), condominio.getId())).thenReturn(Optional.of(semUnidade));

        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(semUnidade.getId(), auth(morador)));
    }

    @Test
    void retiradaSemUnidadeConfereOCodigoERegistraOApartamentoInformado() {
        var semUnidade = encomenda(null, "AGUARDANDO RETIRADA");
        semUnidade.setCodigoIdentificacao("BR999XX");
        when(encomendaRepository.findByIdAndCondominioId(semUnidade.getId(), condominio.getId())).thenReturn(Optional.of(semUnidade));
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        when(apartamentoRepository.findByIdAndCondominioId(unidade102.getId(), condominio.getId())).thenReturn(Optional.of(unidade102));
        when(encomendaRepository.save(semUnidade)).thenReturn(semUnidade);

        // Código que não confere: nada muda
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarRetirada(semUnidade.getId(), new RetiradaEncomendaDTO("Maria", "BR000", unidade102.getId()), auth(porteiro)));
        // Sem código ou sem apartamento informado também não
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarRetirada(semUnidade.getId(), new RetiradaEncomendaDTO("Maria", " ", unidade102.getId()), auth(porteiro)));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarRetirada(semUnidade.getId(), new RetiradaEncomendaDTO("Maria", "BR999XX", null), auth(porteiro)));
        assertEquals("AGUARDANDO RETIRADA", semUnidade.getStatus());

        // Código conferido (sem diferenciar maiúsculas e espaços): retira e guarda quem e de qual apartamento
        var resposta = service.registrarRetirada(semUnidade.getId(), new RetiradaEncomendaDTO(" Maria Lima ", "br 999 xx", unidade102.getId()), auth(porteiro));

        assertEquals("RETIRADA", resposta.status());
        assertEquals("Maria Lima", resposta.retiranteNome());
        assertEquals("102", resposta.retiranteApartamentoNumero());
        // O apartamento informado só fica registrado: a encomenda continua sem unidade
        assertTrue(resposta.semDestinatario());
    }

    @Test
    void todaRetiradaExigeONomeDeQuemRetira() {
        var aberta = encomenda(unidade101, "AGUARDANDO RETIRADA");

        assertThrows(IllegalArgumentException.class, () -> service.registrarRetirada(aberta.getId(), new RetiradaEncomendaDTO(" ", null, null), auth(porteiro)));
        assertThrows(IllegalArgumentException.class, () -> service.registrarRetirada(aberta.getId(), null, auth(porteiro)));
        verify(encomendaRepository, never()).save(any());
    }
}
