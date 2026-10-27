package br.com.condomais.interativo.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.AreaComum;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.repository.AreaComumRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.dto.SolicitacaoReservaDTO;
import br.com.condomais.interativo.model.Reserva;
import br.com.condomais.interativo.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock private ReservaRepository reservaRepository;
    @Mock private AreaComumRepository areaComumRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private ReservaService service;

    private final LocalDate semanaQueVem = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(7);
    private Condominio condominio;
    private AreaComum salao;
    private Apartamento unidade101;
    private Usuario proprietario101;
    private Usuario inquilino101;
    private Usuario moradorOutraUnidade;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        salao = new AreaComum();
        salao.setId(UUID.randomUUID());
        salao.setNome("Salão de festas");
        salao.setReservavel(true);
        salao.setExigeAprovacao(true);
        salao.setCondominio(condominio);
        unidade101 = unidade("101");
        proprietario101 = usuario("MORADOR", unidade101, "PROPRIETARIO");
        inquilino101 = usuario("MORADOR", unidade101, "INQUILINO");
        moradorOutraUnidade = usuario("MORADOR", unidade("102"), "PROPRIETARIO");
        admin = usuario("ADMIN", null, null);
    }

    private Apartamento unidade(String numero) {
        var apartamento = new Apartamento();
        apartamento.setId(UUID.randomUUID());
        apartamento.setNumero(numero);
        apartamento.setCondominio(condominio);
        return apartamento;
    }

    private Usuario usuario(String perfil, Apartamento apartamento, String vinculo) {
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome(perfil + " " + vinculo);
        usuario.setPerfil(perfil);
        usuario.setApartamento(apartamento);
        usuario.setVinculo(vinculo);
        usuario.setCondominio(condominio);
        lenient().when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private Reserva reserva(Usuario morador, String status, LocalDate data) {
        var reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setArea(salao);
        reserva.setMorador(morador);
        reserva.setStatus(status);
        reserva.setDataReserva(data);
        reserva.setHorarioInicio(LocalTime.of(14, 0));
        reserva.setHorarioFim(LocalTime.of(18, 0));
        reserva.setCondominio(condominio);
        lenient().when(reservaRepository.findByIdAndCondominioId(reserva.getId(), condominio.getId())).thenReturn(Optional.of(reserva));
        return reserva;
    }

    private SolicitacaoReservaDTO pedido() {
        return new SolicitacaoReservaDTO(salao.getId(), semanaQueVem, LocalTime.of(14, 0), LocalTime.of(18, 0));
    }

    private void areaDisponivel() {
        when(areaComumRepository.findParaReserva(salao.getId(), condominio.getId())).thenReturn(Optional.of(salao));
        when(reservaRepository.existeConflitoDeHorario(any(), any(), any(), any())).thenReturn(false);
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static UsuarioAutenticado auth(Usuario usuario) {
        return new UsuarioAutenticado(usuario);
    }

    // ---------------------------------------------------------------------
    // Solicitação
    // ---------------------------------------------------------------------

    @Test
    void proprietarioConfirmaDiretoEmAreaQueExigeAprovacao() {
        areaDisponivel();

        var resposta = service.solicitar(pedido(), auth(proprietario101));

        assertEquals("CONFIRMADA", resposta.status());
        assertEquals("101", resposta.apartamentoNumero());
        assertEquals("Salão de festas", resposta.areaNome());
    }

    @Test
    void inquilinoAguardaAprovacao() {
        areaDisponivel();

        assertEquals("AGUARDANDO APROVACAO", service.solicitar(pedido(), auth(inquilino101)).status());
    }

    @Test
    void areaSemAprovacaoConfirmaParaQualquerMorador() {
        salao.setExigeAprovacao(false);
        areaDisponivel();

        assertEquals("CONFIRMADA", service.solicitar(pedido(), auth(inquilino101)).status());
    }

    @Test
    void horarioOcupadoEhConflito() {
        when(areaComumRepository.findParaReserva(salao.getId(), condominio.getId())).thenReturn(Optional.of(salao));
        when(reservaRepository.existeConflitoDeHorario(salao.getId(), semanaQueVem, LocalTime.of(14, 0), LocalTime.of(18, 0))).thenReturn(true);

        assertThrows(ConflitoException.class, () -> service.solicitar(pedido(), auth(proprietario101)));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void areaDesativadaNaoAceitaReservaNova() {
        salao.setAtivo(false);
        when(areaComumRepository.findParaReserva(salao.getId(), condominio.getId())).thenReturn(Optional.of(salao));

        var erro = assertThrows(IllegalArgumentException.class, () -> service.solicitar(pedido(), auth(proprietario101)));
        assertTrue(erro.getMessage().contains("desativada"));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void validacoesDeHorario() {
        var invertido = new SolicitacaoReservaDTO(salao.getId(), semanaQueVem, LocalTime.of(18, 0), LocalTime.of(14, 0));
        var passado = new SolicitacaoReservaDTO(salao.getId(), semanaQueVem.minusDays(30), LocalTime.of(14, 0), LocalTime.of(18, 0));
        var incompleto = new SolicitacaoReservaDTO(salao.getId(), semanaQueVem, null, LocalTime.of(18, 0));

        assertThrows(IllegalArgumentException.class, () -> service.solicitar(invertido, auth(proprietario101)));
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(passado, auth(proprietario101)));
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(incompleto, auth(proprietario101)));
        verifyNoInteractions(areaComumRepository);
    }

    @Test
    void soMoradorSolicita() {
        assertThrows(SecurityException.class, () -> service.solicitar(pedido(), auth(admin)));
        verifyNoInteractions(areaComumRepository, reservaRepository);
    }

    // ---------------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------------

    @Test
    void moradorListaAsDaPropriaUnidade() {
        var aguardando = reserva(inquilino101, "AGUARDANDO APROVACAO", semanaQueVem);
        var confirmada = reserva(proprietario101, "CONFIRMADA", semanaQueVem);
        when(reservaRepository.listarDaUnidade(condominio.getId(), unidade101.getId())).thenReturn(List.of(aguardando, confirmada));

        var lista = service.listar(null, null, "aguardando_aprovacao", auth(proprietario101));

        assertEquals(1, lista.size());
        assertEquals("AGUARDANDO APROVACAO", lista.get(0).status());
        verify(reservaRepository, never()).listarDoCondominio(any());
    }

    @Test
    void adminListaTodasEFiltraPorAreaEData() {
        var outraArea = new AreaComum();
        outraArea.setId(UUID.randomUUID());
        outraArea.setNome("Churrasqueira");
        var naChurrasqueira = reserva(moradorOutraUnidade, "CONFIRMADA", semanaQueVem);
        naChurrasqueira.setArea(outraArea);
        var noSalao = reserva(inquilino101, "CONFIRMADA", semanaQueVem);
        var noDiaSeguinte = reserva(proprietario101, "CONFIRMADA", semanaQueVem.plusDays(1));
        when(reservaRepository.listarDoCondominio(condominio.getId())).thenReturn(List.of(noSalao, naChurrasqueira, noDiaSeguinte));

        assertEquals(3, service.listar(null, null, null, auth(admin)).size());
        assertEquals(1, service.listar(salao.getId(), semanaQueVem, null, auth(admin)).size());
    }

    @Test
    void moradorNaoAbreReservaDeOutraUnidade() {
        var deOutra = reserva(moradorOutraUnidade, "CONFIRMADA", semanaQueVem);

        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(deOutra.getId(), auth(inquilino101)));
    }

    @Test
    void ocupacaoTrazSoOsHorarios() {
        when(areaComumRepository.findByIdAndCondominioId(salao.getId(), condominio.getId())).thenReturn(Optional.of(salao));
        var ocupando = reserva(inquilino101, "CONFIRMADA", semanaQueVem);
        when(reservaRepository.listarOcupacao(condominio.getId(), salao.getId(), semanaQueVem)).thenReturn(List.of(ocupando));

        var ocupados = service.ocupacao(salao.getId(), semanaQueVem, auth(moradorOutraUnidade));

        assertEquals(1, ocupados.size());
        assertEquals(LocalTime.of(14, 0), ocupados.get(0).horarioInicio());
    }

    @Test
    void statusInvalidoEhRecusado() {
        assertThrows(IllegalArgumentException.class, () -> service.listar(null, null, "PERDIDA", auth(admin)));
    }

    // ---------------------------------------------------------------------
    // Cancelar, aprovar e recusar
    // ---------------------------------------------------------------------

    @Test
    void quemPediuCancela() {
        var minha = reserva(inquilino101, "CONFIRMADA", semanaQueVem);
        when(reservaRepository.save(minha)).thenReturn(minha);

        assertEquals("CANCELADA", service.cancelar(minha.getId(), auth(inquilino101)).status());
    }

    @Test
    void outroMoradorNaoCancela() {
        var deOutro = reserva(inquilino101, "CONFIRMADA", semanaQueVem);

        assertThrows(SecurityException.class, () -> service.cancelar(deOutro.getId(), auth(proprietario101)));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void naoCancelaReservaJaCanceladaNemQueJaPassou() {
        var cancelada = reserva(inquilino101, "CANCELADA", semanaQueVem);
        var passada = reserva(inquilino101, "CONFIRMADA", semanaQueVem.minusDays(30));

        assertThrows(ConflitoException.class, () -> service.cancelar(cancelada.getId(), auth(inquilino101)));
        assertThrows(IllegalArgumentException.class, () -> service.cancelar(passada.getId(), auth(admin)));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void proprietarioDaUnidadeAprovaOPedidoDoInquilino() {
        var pedido = reserva(inquilino101, "AGUARDANDO APROVACAO", semanaQueVem);
        when(reservaRepository.save(pedido)).thenReturn(pedido);

        var resposta = service.aprovar(pedido.getId(), auth(proprietario101));

        assertEquals("CONFIRMADA", resposta.status());
        assertEquals(proprietario101.getNome(), resposta.aprovadorNome());
    }

    @Test
    void adminRecusa() {
        var pedido = reserva(inquilino101, "AGUARDANDO APROVACAO", semanaQueVem);
        when(reservaRepository.save(pedido)).thenReturn(pedido);

        assertEquals("RECUSADA", service.recusar(pedido.getId(), auth(admin)).status());
    }

    @Test
    void naoAprovaQuemNaoEhProprietarioDaUnidade() {
        var pedido = reserva(inquilino101, "AGUARDANDO APROVACAO", semanaQueVem);
        var outroInquilino = usuario("MORADOR", unidade101, "INQUILINO");
        var portaria = usuario("PORTARIA", null, null);

        assertThrows(SecurityException.class, () -> service.aprovar(pedido.getId(), auth(moradorOutraUnidade)));
        assertThrows(SecurityException.class, () -> service.aprovar(pedido.getId(), auth(outroInquilino)));
        assertThrows(SecurityException.class, () -> service.aprovar(pedido.getId(), auth(portaria)));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void soAprovaQuemEstaAguardando() {
        var confirmada = reserva(inquilino101, "CONFIRMADA", semanaQueVem);

        assertThrows(ConflitoException.class, () -> service.aprovar(confirmada.getId(), auth(admin)));
    }
}
