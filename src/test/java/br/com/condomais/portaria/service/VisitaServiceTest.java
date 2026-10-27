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
import br.com.condomais.portaria.dto.AutorizacaoVisitaDTO;
import br.com.condomais.portaria.dto.RegistroEntradaDTO;
import br.com.condomais.portaria.model.Visita;
import br.com.condomais.portaria.model.Visitante;
import br.com.condomais.portaria.repository.VisitaRepository;
import br.com.condomais.portaria.repository.VisitanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisitaServiceTest {

    private static final String CPF = "529.982.247-25";

    @Mock private VisitaRepository visitaRepository;
    @Mock private VisitanteRepository visitanteRepository;
    @Mock private ApartamentoRepository apartamentoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private VisitaService service;

    private Condominio condominio;
    private Apartamento unidade101;
    private Apartamento unidade102;
    private Usuario porteiro;
    private Usuario admin;
    private Usuario morador101;
    private Usuario morador102;

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        var torre = new Torre();
        torre.setNome("Torre A");
        unidade101 = unidade("101", torre);
        unidade102 = unidade("102", torre);
        porteiro = usuario("PORTARIA", "Porteiro", null);
        admin = usuario("ADMIN", "Admin", null);
        morador101 = usuario("MORADOR", "Morador 101", unidade101);
        morador102 = usuario("MORADOR", "Morador 102", unidade102);
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

    private static UsuarioAutenticado auth(Usuario usuario) {
        return new UsuarioAutenticado(usuario);
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).truncatedTo(ChronoUnit.MINUTES);
    }

    // Visita com período de entrada entre inicio e fim
    private Visita visita(Apartamento apartamento, String status, LocalDateTime inicio, LocalDateTime fim) {
        var visitante = new Visitante();
        visitante.setId(UUID.randomUUID());
        visitante.setNome("Mariana");
        visitante.setCpf(CPF);
        visitante.setCondominio(condominio);
        var visita = new Visita();
        visita.setId(UUID.randomUUID());
        visita.setVisitante(visitante);
        visita.setApartamento(apartamento);
        visita.setCondominio(condominio);
        visita.setStatus(status);
        visita.setDataPrevista(inicio.toLocalDate());
        visita.setHorarioPrevisto(inicio.toLocalTime());
        visita.setDataFimPrevista(fim.toLocalDate());
        visita.setHorarioFimPrevisto(fim.toLocalTime());
        return visita;
    }

    private Visita visitaAgora(Apartamento apartamento, String status) {
        return visita(apartamento, status, agora().minusHours(1), agora().plusHours(2));
    }

    private AutorizacaoVisitaDTO pedido(UUID apartamentoId, UUID moradorId, Boolean autorizado) {
        var inicio = agora().plusHours(1);
        var fim = inicio.plusHours(3);
        return new AutorizacaoVisitaDTO("52998224725", " Mariana Souza ", "(81) 98888-0001", apartamentoId, moradorId,
                inicio.toLocalDate(), inicio.toLocalTime(), fim.toLocalDate(), fim.toLocalTime(), "Visita familiar", autorizado);
    }

    private void salvarDevolveOArgumento() {
        when(visitanteRepository.save(any(Visitante.class))).thenAnswer(i -> i.getArgument(0));
        when(visitaRepository.save(any(Visita.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------------------------------------------------------------------
    // Cadastro
    // ---------------------------------------------------------------------

    @Test
    void moradorAutorizaParaAPropriaUnidadeMesmoPedindoOutra() {
        when(usuarioRepository.findById(morador101.getId())).thenReturn(Optional.of(morador101));
        when(visitanteRepository.findByCondominioIdAndCpf(condominio.getId(), CPF)).thenReturn(Optional.empty());
        salvarDevolveOArgumento();

        var resposta = service.cadastrar(pedido(unidade102.getId(), morador102.getId(), false), auth(morador101));

        assertEquals("AUTORIZADO", resposta.status());
        assertEquals("MORADOR", resposta.origem());
        assertEquals("101", resposta.apartamentoNumero());
        assertEquals("Morador 101", resposta.moradorNome());
        assertEquals("Mariana Souza", resposta.visitanteNome());
        assertEquals(CPF, resposta.visitanteCpf());
        assertNotNull(resposta.dataHoraRegistro());
        verifyNoInteractions(apartamentoRepository);
    }

    @Test
    void moradorSemUnidadeNaoAutoriza() {
        var semUnidade = usuario("MORADOR", "Sem unidade", null);
        when(usuarioRepository.findById(semUnidade.getId())).thenReturn(Optional.of(semUnidade));

        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(pedido(null, null, null), auth(semUnidade)));
        verify(visitaRepository, never()).save(any());
    }

    @Test
    void portariaCadastraPendenteOuJaAutorizada() {
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        when(apartamentoRepository.findByIdAndCondominioId(unidade101.getId(), condominio.getId())).thenReturn(Optional.of(unidade101));
        when(usuarioRepository.findByIdAndCondominioId(morador101.getId(), condominio.getId())).thenReturn(Optional.of(morador101));
        salvarDevolveOArgumento();

        var pendente = service.cadastrar(pedido(unidade101.getId(), morador101.getId(), null), auth(porteiro));
        var autorizada = service.cadastrar(pedido(unidade101.getId(), null, true), auth(porteiro));

        assertEquals("PENDENTE", pendente.status());
        assertEquals("PORTARIA", pendente.origem());
        assertEquals("Morador 101", pendente.moradorNome());
        assertEquals("AUTORIZADO", autorizada.status());
        assertNull(autorizada.moradorNome());
    }

    @Test
    void moradorResponsavelPrecisaSerDaUnidadeVisitada() {
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        when(apartamentoRepository.findByIdAndCondominioId(unidade101.getId(), condominio.getId())).thenReturn(Optional.of(unidade101));
        when(usuarioRepository.findByIdAndCondominioId(morador102.getId(), condominio.getId())).thenReturn(Optional.of(morador102));

        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(pedido(unidade101.getId(), morador102.getId(), null), auth(porteiro)));
    }

    @Test
    void unidadeDeOutroCondominioNaoEhEncontrada() {
        var deOutro = UUID.randomUUID();
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        when(apartamentoRepository.findByIdAndCondominioId(deOutro, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.cadastrar(pedido(deOutro, null, null), auth(porteiro)));
    }

    @Test
    void visitanteEhReaproveitadoPeloCpfNoCondominioComNomeAtualizado() {
        var existente = new Visitante();
        existente.setId(UUID.randomUUID());
        existente.setCpf(CPF);
        existente.setNome("Mari");
        existente.setTelefone("(81) 3333-0000");
        existente.setCondominio(condominio);
        when(usuarioRepository.findById(morador101.getId())).thenReturn(Optional.of(morador101));
        when(visitanteRepository.findByCondominioIdAndCpf(condominio.getId(), CPF)).thenReturn(Optional.of(existente));
        salvarDevolveOArgumento();

        var resposta = service.cadastrar(pedido(null, null, null), auth(morador101));

        assertEquals("Mariana Souza", resposta.visitanteNome());
        assertEquals("(81) 98888-0001", resposta.visitanteTelefone());
        verify(visitanteRepository).save(existente);
    }

    @Test
    void validacoesDoCadastro() {
        when(usuarioRepository.findById(morador101.getId())).thenReturn(Optional.of(morador101));
        var inicio = agora().plusHours(1);

        // CPF inválido
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(new AutorizacaoVisitaDTO("123", "Ana", null, null, null,
                inicio.toLocalDate(), inicio.toLocalTime(), inicio.toLocalDate(), inicio.toLocalTime().plusMinutes(30), null, null), auth(morador101)));
        // sem nome
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(new AutorizacaoVisitaDTO(CPF, " ", null, null, null,
                inicio.toLocalDate(), inicio.toLocalTime(), inicio.plusHours(1).toLocalDate(), inicio.plusHours(1).toLocalTime(), null, null), auth(morador101)));
        // sem período
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(new AutorizacaoVisitaDTO(CPF, "Ana", null, null, null,
                null, null, null, null, null, null), auth(morador101)));
        // término antes do início
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(new AutorizacaoVisitaDTO(CPF, "Ana", null, null, null,
                inicio.toLocalDate(), inicio.toLocalTime(), inicio.minusHours(1).toLocalDate(), inicio.minusHours(1).toLocalTime(), null, null), auth(morador101)));
        // período que já terminou
        var ontem = agora().minusDays(1);
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(new AutorizacaoVisitaDTO(CPF, "Ana", null, null, null,
                ontem.toLocalDate(), ontem.toLocalTime(), ontem.plusHours(2).toLocalDate(), ontem.plusHours(2).toLocalTime(), null, null), auth(morador101)));
        verify(visitaRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------------

    @Test
    void portariaListaTodasComStatusEfetivo() {
        var vencida = visita(unidade101, "AUTORIZADO", agora().minusHours(5), agora().minusHours(1));
        var valida = visitaAgora(unidade102, "PENDENTE");
        when(visitaRepository.listarDoCondominio(condominio.getId())).thenReturn(List.of(valida, vencida));

        var lista = service.listar(null, null, null, auth(porteiro));

        assertEquals(List.of("PENDENTE", "EXPIRADO"), lista.stream().map(v -> v.status()).toList());
        assertEquals("Torre A", lista.get(0).torreNome());
    }

    @Test
    void filtrosDeStatusUnidadeEData() {
        var vencida = visita(unidade101, "AUTORIZADO", agora().minusHours(5), agora().minusHours(1));
        var hoje101 = visitaAgora(unidade101, "ENTROU");
        var semanaQueVem = visita(unidade102, "AUTORIZADO", agora().plusDays(7), agora().plusDays(7).plusHours(2));
        when(visitaRepository.listarDoCondominio(condominio.getId())).thenReturn(List.of(semanaQueVem, hoje101, vencida));

        assertEquals(List.of(vencida.getId()), service.listar("expirado", null, null, auth(admin)).stream().map(v -> v.id()).toList());
        assertEquals(2, service.listar(null, unidade101.getId(), null, auth(admin)).size());
        assertEquals(List.of(semanaQueVem.getId()),
                service.listar(null, null, agora().plusDays(7).toLocalDate(), auth(admin)).stream().map(v -> v.id()).toList());
        assertThrows(IllegalArgumentException.class, () -> service.listar("SUMIU", null, null, auth(admin)));
    }

    @Test
    void moradorListaSoAPropriaUnidade() {
        when(usuarioRepository.findById(morador101.getId())).thenReturn(Optional.of(morador101));
        when(visitaRepository.listarDaUnidade(condominio.getId(), unidade101.getId())).thenReturn(List.of(visitaAgora(unidade101, "AUTORIZADO")));

        var lista = service.listar(null, unidade102.getId(), null, auth(morador101));

        assertEquals(1, lista.size());
        verify(visitaRepository, never()).listarDoCondominio(any());
    }

    @Test
    void moradorNaoConsultaVisitaDeOutraUnidade() {
        var visita = visitaAgora(unidade102, "AUTORIZADO");
        when(visitaRepository.findByIdAndCondominioId(visita.getId(), condominio.getId())).thenReturn(Optional.of(visita));
        when(usuarioRepository.findById(morador101.getId())).thenReturn(Optional.of(morador101));

        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(visita.getId(), auth(morador101)));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.autorizar(visita.getId(), auth(morador101)));
    }

    // ---------------------------------------------------------------------
    // Autorizar e recusar
    // ---------------------------------------------------------------------

    @Test
    void moradorDaUnidadeAutorizaPendenteEViraResponsavel() {
        var visita = visitaAgora(unidade101, "PENDENTE");
        when(visitaRepository.findByIdAndCondominioId(visita.getId(), condominio.getId())).thenReturn(Optional.of(visita));
        when(usuarioRepository.findById(morador101.getId())).thenReturn(Optional.of(morador101));
        when(visitaRepository.save(visita)).thenReturn(visita);

        var resposta = service.autorizar(visita.getId(), auth(morador101));

        assertEquals("AUTORIZADO", resposta.status());
        assertEquals("Morador 101", resposta.moradorNome());
    }

    @Test
    void soPendenteNoPrazoPodeSerAutorizada() {
        var autorizada = visitaAgora(unidade101, "AUTORIZADO");
        var vencida = visita(unidade101, "PENDENTE", agora().minusHours(5), agora().minusHours(1));
        when(visitaRepository.findByIdAndCondominioId(autorizada.getId(), condominio.getId())).thenReturn(Optional.of(autorizada));
        when(visitaRepository.findByIdAndCondominioId(vencida.getId(), condominio.getId())).thenReturn(Optional.of(vencida));

        assertThrows(ConflitoException.class, () -> service.autorizar(autorizada.getId(), auth(porteiro)));
        var erro = assertThrows(ConflitoException.class, () -> service.autorizar(vencida.getId(), auth(porteiro)));
        assertTrue(erro.getMessage().contains("expirou"));
        verify(visitaRepository, never()).save(any());
    }

    @Test
    void recusaPendenteOuAutorizadaMasNaoQuemJaEntrou() {
        var pendente = visitaAgora(unidade101, "PENDENTE");
        var autorizada = visitaAgora(unidade101, "AUTORIZADO");
        var entrou = visitaAgora(unidade101, "ENTROU");
        for (var v : List.of(pendente, autorizada, entrou)) {
            when(visitaRepository.findByIdAndCondominioId(v.getId(), condominio.getId())).thenReturn(Optional.of(v));
        }
        when(visitaRepository.save(any(Visita.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("RECUSADO", service.recusar(pendente.getId(), auth(porteiro)).status());
        assertEquals("RECUSADO", service.recusar(autorizada.getId(), auth(admin)).status());
        assertThrows(ConflitoException.class, () -> service.recusar(entrou.getId(), auth(porteiro)));
    }

    // ---------------------------------------------------------------------
    // Entrada e saída
    // ---------------------------------------------------------------------

    @Test
    void entradaDeVisitaAutorizadaDentroDoPeriodo() {
        var visita = visitaAgora(unidade101, "AUTORIZADO");
        when(visitaRepository.findByIdAndCondominioId(visita.getId(), condominio.getId())).thenReturn(Optional.of(visita));
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        when(visitaRepository.save(visita)).thenReturn(visita);

        var resposta = service.registrarEntrada(visita.getId(), auth(porteiro));

        assertEquals("ENTROU", resposta.status());
        assertNotNull(resposta.dataHoraEntrada());
        assertEquals("Porteiro", resposta.porteiroEntradaNome());
    }

    @Test
    void entradaForaDoPeriodoOuSemAutorizacaoEhRecusada() {
        var cedo = visita(unidade101, "AUTORIZADO", agora().plusHours(2), agora().plusHours(4));
        var vencida = visita(unidade101, "AUTORIZADO", agora().minusHours(4), agora().minusHours(2));
        var pendente = visitaAgora(unidade101, "PENDENTE");
        for (var v : List.of(cedo, vencida, pendente)) {
            when(visitaRepository.findByIdAndCondominioId(v.getId(), condominio.getId())).thenReturn(Optional.of(v));
        }

        assertTrue(assertThrows(ConflitoException.class, () -> service.registrarEntrada(cedo.getId(), auth(porteiro))).getMessage().contains("ainda não começou"));
        assertTrue(assertThrows(ConflitoException.class, () -> service.registrarEntrada(vencida.getId(), auth(porteiro))).getMessage().contains("expirou"));
        assertThrows(ConflitoException.class, () -> service.registrarEntrada(pendente.getId(), auth(porteiro)));
        verify(visitaRepository, never()).save(any());
    }

    @Test
    void saidaSoDeQuemEntrouENaoDuasVezes() {
        var entrou = visitaAgora(unidade101, "ENTROU");
        var saiu = visitaAgora(unidade101, "SAIU");
        when(visitaRepository.findByIdAndCondominioId(entrou.getId(), condominio.getId())).thenReturn(Optional.of(entrou));
        when(visitaRepository.findByIdAndCondominioId(saiu.getId(), condominio.getId())).thenReturn(Optional.of(saiu));
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        when(visitaRepository.save(entrou)).thenReturn(entrou);

        var resposta = service.registrarSaida(entrou.getId(), auth(porteiro));

        assertEquals("SAIU", resposta.status());
        assertEquals("Porteiro", resposta.porteiroSaidaNome());
        assertThrows(ConflitoException.class, () -> service.registrarSaida(saiu.getId(), auth(porteiro)));
    }

    @Test
    void entradaImediataCadastraNoCondominioEJaEntra() {
        when(apartamentoRepository.findByIdAndCondominioId(unidade101.getId(), condominio.getId())).thenReturn(Optional.of(unidade101));
        when(visitanteRepository.findByCondominioIdAndCpf(condominio.getId(), CPF)).thenReturn(Optional.empty());
        when(usuarioRepository.findById(porteiro.getId())).thenReturn(Optional.of(porteiro));
        salvarDevolveOArgumento();

        var resposta = service.registrarEntradaImediata(new RegistroEntradaDTO(CPF, "Entregador", null, unidade101.getId(), null), auth(porteiro));

        assertEquals("ENTROU", resposta.status());
        assertEquals("Porteiro", resposta.porteiroEntradaNome());
        var visitante = ArgumentCaptor.forClass(Visitante.class);
        verify(visitanteRepository).save(visitante.capture());
        assertSame(condominio, visitante.getValue().getCondominio());
    }

    @Test
    void entradaESaidaSoPelaPortariaOuAdmin() {
        var id = UUID.randomUUID();
        assertThrows(SecurityException.class, () -> service.registrarEntrada(id, auth(morador101)));
        assertThrows(SecurityException.class, () -> service.registrarSaida(id, auth(morador101)));
        assertThrows(SecurityException.class,
                () -> service.registrarEntradaImediata(new RegistroEntradaDTO(CPF, "X", null, unidade101.getId(), null), auth(morador101)));
        verifyNoInteractions(visitaRepository);
    }

    @Test
    void visitaDeOutroCondominioNaoEhEncontrada() {
        var id = UUID.randomUUID();
        when(visitaRepository.findByIdAndCondominioId(id, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.registrarSaida(id, auth(porteiro)));
    }
}
