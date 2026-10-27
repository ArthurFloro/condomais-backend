package br.com.condomais.atendimento.service;

import br.com.condomais.atendimento.dto.AberturaChamadoDTO;
import br.com.condomais.atendimento.dto.AtualizacaoChamadoDTO;
import br.com.condomais.atendimento.dto.ComentarioChamadoDTO;
import br.com.condomais.atendimento.model.CategoriaChamado;
import br.com.condomais.atendimento.model.Chamado;
import br.com.condomais.atendimento.model.HistoricoChamado;
import br.com.condomais.atendimento.repository.CategoriaChamadoRepository;
import br.com.condomais.atendimento.repository.ChamadoRepository;
import br.com.condomais.atendimento.repository.HistoricoChamadoRepository;
import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChamadoServiceTest {

    @Mock private ChamadoRepository chamadoRepository;
    @Mock private CategoriaChamadoRepository categoriaRepository;
    @Mock private ApartamentoRepository apartamentoRepository;
    @Mock private HistoricoChamadoRepository historicoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CategoriaChamadoService categoriaService;
    @InjectMocks private ChamadoService service;

    private Condominio condominio;
    private CategoriaChamado eletrica;
    private Apartamento unidade101;
    private Apartamento unidade102;
    private Usuario morador101;
    private Usuario vizinho101;
    private Usuario morador102;
    private Usuario admin;
    private Usuario portaria;

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        eletrica = new CategoriaChamado();
        eletrica.setId(UUID.randomUUID());
        eletrica.setNome("Elétrica");
        eletrica.setCondominio(condominio);
        unidade101 = unidade("101");
        unidade102 = unidade("102");
        morador101 = usuario("MORADOR", unidade101);
        vizinho101 = usuario("MORADOR", unidade101);
        morador102 = usuario("MORADOR", unidade102);
        admin = usuario("ADMIN", null);
        portaria = usuario("PORTARIA", null);
        lenient().when(historicoRepository.findByChamadoIdOrderByDataHoraAsc(any())).thenReturn(List.of());
    }

    private Apartamento unidade(String numero) {
        var apartamento = new Apartamento();
        apartamento.setId(UUID.randomUUID());
        apartamento.setNumero(numero);
        apartamento.setCondominio(condominio);
        return apartamento;
    }

    private Usuario usuario(String perfil, Apartamento apartamento) {
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome(perfil + " " + (apartamento != null ? apartamento.getNumero() : ""));
        usuario.setPerfil(perfil);
        usuario.setApartamento(apartamento);
        usuario.setCondominio(condominio);
        lenient().when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private Chamado chamado(Usuario solicitante, Apartamento apartamento, String status) {
        var chamado = new Chamado();
        chamado.setId(UUID.randomUUID());
        chamado.setTitulo("Lâmpada queimada");
        chamado.setDescricao("No hall");
        chamado.setPrioridade("NORMAL");
        chamado.setStatus(status);
        chamado.setDataAbertura(LocalDateTime.now());
        chamado.setCategoria(eletrica);
        chamado.setSolicitante(solicitante);
        chamado.setApartamento(apartamento);
        chamado.setCondominio(condominio);
        lenient().when(chamadoRepository.findByIdAndCondominioId(chamado.getId(), condominio.getId())).thenReturn(Optional.of(chamado));
        return chamado;
    }

    private static UsuarioAutenticado auth(Usuario usuario) {
        return new UsuarioAutenticado(usuario);
    }

    private void categoriaDisponivel() {
        when(categoriaRepository.findByIdAndCondominioId(eletrica.getId(), condominio.getId())).thenReturn(Optional.of(eletrica));
        when(chamadoRepository.save(any(Chamado.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------------------------------------------------------------------
    // Abertura
    // ---------------------------------------------------------------------

    @Test
    void moradorAbreNaPropriaUnidadeComPrioridadePadraoEHistorico() {
        categoriaDisponivel();
        when(apartamentoRepository.findByIdAndCondominioId(unidade101.getId(), condominio.getId())).thenReturn(Optional.of(unidade101));

        var resposta = service.abrir(new AberturaChamadoDTO(" Vazamento ", "Pia da cozinha", eletrica.getId(), unidade101.getId(), null, null), auth(morador101));

        assertEquals("Vazamento", resposta.titulo());
        assertEquals("ABERTO", resposta.status());
        assertEquals("NORMAL", resposta.prioridade());
        assertEquals("101", resposta.apartamentoNumero());
        var historico = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historicoRepository).save(historico.capture());
        assertEquals("ABERTURA", historico.getValue().getAcao());
    }

    @Test
    void moradorNaoAbreChamadoEmNomeDeOutraUnidade() {
        when(categoriaRepository.findByIdAndCondominioId(eletrica.getId(), condominio.getId())).thenReturn(Optional.of(eletrica));

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.abrir(new AberturaChamadoDTO("T", "D", eletrica.getId(), unidade102.getId(), null, null), auth(morador101)));
        verify(chamadoRepository, never()).save(any());
    }

    @Test
    void portariaAbrePorQualquerUnidadeEmoradorPorAreaComum() {
        categoriaDisponivel();
        when(apartamentoRepository.findByIdAndCondominioId(unidade102.getId(), condominio.getId())).thenReturn(Optional.of(unidade102));

        assertEquals("102", service.abrir(new AberturaChamadoDTO("Barulho", "Festa", eletrica.getId(), unidade102.getId(), "alta", null), auth(portaria)).apartamentoNumero());
        assertNull(service.abrir(new AberturaChamadoDTO("Piscina suja", "Folhas", eletrica.getId(), null, null, null), auth(morador101)).apartamentoId());
    }

    @Test
    void validacoesDaAbertura() {
        when(categoriaRepository.findByIdAndCondominioId(eletrica.getId(), condominio.getId())).thenReturn(Optional.of(eletrica));
        assertThrows(IllegalArgumentException.class,
                () -> service.abrir(new AberturaChamadoDTO(" ", "D", eletrica.getId(), null, null, null), auth(morador101)));
        assertThrows(IllegalArgumentException.class,
                () -> service.abrir(new AberturaChamadoDTO("T", "D", eletrica.getId(), null, "CRITICA", null), auth(morador101)));
        verify(chamadoRepository, never()).save(any());
    }

    @Test
    void categoriaDeOutroCondominioNaoEhEncontrada() {
        var deOutro = UUID.randomUUID();
        when(categoriaRepository.findByIdAndCondominioId(deOutro, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.abrir(new AberturaChamadoDTO("T", "D", deOutro, null, null, null), auth(morador101)));
    }

    // ---------------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------------

    @Test
    void moradorListaOsDeleEOsDaUnidade() {
        var doVizinho = chamado(vizinho101, unidade101, "ABERTO");
        when(chamadoRepository.listarDoMorador(condominio.getId(), morador101.getId(), unidade101.getId())).thenReturn(List.of(doVizinho));

        assertEquals(1, service.listar(null, null, auth(morador101)).size());
        verify(chamadoRepository, never()).findByCondominioIdOrderByDataAberturaDesc(any());
    }

    @Test
    void adminListaTodosEFiltraPorStatusSemAcento() {
        var aberto = chamado(morador101, unidade101, "ABERTO");
        var emAnalise = chamado(morador102, unidade102, "EM ANALISE");
        when(chamadoRepository.findByCondominioIdOrderByDataAberturaDesc(condominio.getId())).thenReturn(List.of(aberto, emAnalise));

        assertEquals(2, service.listar(null, null, auth(admin)).size());
        assertEquals(List.of(emAnalise.getId()), service.listar("em análise", null, auth(admin)).stream().map(c -> c.id()).toList());
        assertEquals(1, service.listar("EM_ANALISE", null, auth(admin)).size());
    }

    @Test
    void moradorNaoAbreChamadoDeOutraUnidade() {
        var daOutra = chamado(morador102, unidade102, "ABERTO");

        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(daOutra.getId(), auth(morador101)));
    }

    @Test
    void detalheTrazOHistorico() {
        var meu = chamado(morador101, unidade101, "ABERTO");
        var abertura = new HistoricoChamado();
        abertura.setAcao("ABERTURA");
        abertura.setDataHora(LocalDateTime.now());
        abertura.setUsuario(morador101);
        when(historicoRepository.findByChamadoIdOrderByDataHoraAsc(meu.getId())).thenReturn(List.of(abertura));

        var resposta = service.buscar(meu.getId(), auth(morador101));

        assertEquals(1, resposta.historico().size());
        assertEquals("ABERTURA", resposta.historico().get(0).acao());
    }

    // ---------------------------------------------------------------------
    // Andamento e comentários
    // ---------------------------------------------------------------------

    @Test
    void adminMudaStatusERegistraNoHistorico() {
        var chamado = chamado(morador101, unidade101, "ABERTO");
        when(chamadoRepository.save(chamado)).thenReturn(chamado);

        var resposta = service.atualizar(chamado.getId(), new AtualizacaoChamadoDTO("em_atendimento", null, "Eletricista agendado", "João Eletricista", "(81) 90000-0000", LocalDate.now().plusDays(2)), auth(admin));

        assertEquals("EM ATENDIMENTO", resposta.status());
        assertEquals("João Eletricista", resposta.responsavelExterno());
        var historico = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historicoRepository, times(2)).save(historico.capture());
        assertEquals("STATUS: ABERTO -> EM ATENDIMENTO", historico.getAllValues().get(0).getAcao());
        assertEquals("Eletricista agendado", historico.getAllValues().get(0).getComentario());
    }

    @Test
    void encerrarRegistraADataEDepoisNaoMudaMais() {
        var chamado = chamado(morador101, unidade101, "RESOLVIDO");
        when(chamadoRepository.save(chamado)).thenReturn(chamado);

        var resposta = service.atualizar(chamado.getId(), new AtualizacaoChamadoDTO("ENCERRADO", null, null, null, null, null), auth(admin));

        assertNotNull(resposta.dataEncerramento());
        assertThrows(ConflitoException.class,
                () -> service.atualizar(chamado.getId(), new AtualizacaoChamadoDTO("ABERTO", null, null, null, null, null), auth(admin)));
        assertThrows(ConflitoException.class,
                () -> service.comentar(chamado.getId(), new ComentarioChamadoDTO("E agora?"), auth(morador101)));
    }

    @Test
    void soAdminAtualizaOAndamento() {
        var chamado = chamado(morador101, unidade101, "ABERTO");

        assertThrows(SecurityException.class,
                () -> service.atualizar(chamado.getId(), new AtualizacaoChamadoDTO("RESOLVIDO", null, null, null, null, null), auth(portaria)));
        assertThrows(SecurityException.class,
                () -> service.atualizar(chamado.getId(), new AtualizacaoChamadoDTO("RESOLVIDO", null, null, null, null, null), auth(morador101)));
        verify(chamadoRepository, never()).save(any());
    }

    @Test
    void atualizacaoVaziaEhRecusada() {
        var chamado = chamado(morador101, unidade101, "ABERTO");

        assertThrows(IllegalArgumentException.class,
                () -> service.atualizar(chamado.getId(), new AtualizacaoChamadoDTO(null, null, " ", null, null, null), auth(admin)));
        assertThrows(IllegalArgumentException.class,
                () -> service.atualizar(chamado.getId(), new AtualizacaoChamadoDTO("PAUSADO", null, null, null, null, null), auth(admin)));
    }

    @Test
    void moradorComentaNoDeleMasNaoNoDeOutraUnidade() {
        var meu = chamado(morador101, unidade101, "EM ATENDIMENTO");
        var daOutra = chamado(morador102, unidade102, "ABERTO");

        service.comentar(meu.getId(), new ComentarioChamadoDTO("Obrigado!"), auth(morador101));

        var historico = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historicoRepository).save(historico.capture());
        assertEquals("COMENTARIO", historico.getValue().getAcao());
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.comentar(daOutra.getId(), new ComentarioChamadoDTO("Oi"), auth(morador101)));
        assertThrows(IllegalArgumentException.class,
                () -> service.comentar(meu.getId(), new ComentarioChamadoDTO(" "), auth(morador101)));
    }

    // ---------------------------------------------------------------------
    // Categoria "Outros"
    // ---------------------------------------------------------------------

    @Test
    void semCategoriaOChamadoVaiParaOutros() {
        var outros = new CategoriaChamado();
        outros.setId(UUID.randomUUID());
        outros.setNome("Outros");
        when(categoriaService.garantirOutros(condominio)).thenReturn(outros);
        when(chamadoRepository.save(any(Chamado.class))).thenAnswer(i -> i.getArgument(0));

        var resposta = service.abrir(new AberturaChamadoDTO("Portão rangendo", "Garagem", null, null, null, null), auth(morador101));

        assertEquals("Outros", resposta.categoriaNome());
        verify(categoriaRepository, never()).findByIdAndCondominioId(any(), any());
    }

    @Test
    void adminRecategorizaERegistraNoHistorico() {
        var outros = new CategoriaChamado();
        outros.setId(UUID.randomUUID());
        outros.setNome("Outros");
        var chamado = chamado(morador101, unidade101, "ABERTO");
        chamado.setCategoria(outros);
        when(categoriaRepository.findByIdAndCondominioId(eletrica.getId(), condominio.getId())).thenReturn(Optional.of(eletrica));
        when(chamadoRepository.save(chamado)).thenReturn(chamado);

        var resposta = service.atualizar(chamado.getId(), new AtualizacaoChamadoDTO(null, eletrica.getId(), "Era elétrica", null, null, null), auth(admin));

        assertEquals("Elétrica", resposta.categoriaNome());
        assertEquals("ABERTO", resposta.status());
        var historico = ArgumentCaptor.forClass(HistoricoChamado.class);
        verify(historicoRepository).save(historico.capture());
        assertEquals("CATEGORIA: Outros -> Elétrica", historico.getValue().getAcao());
        assertEquals("Era elétrica", historico.getValue().getComentario());
    }

    @Test
    void recategorizarParaCategoriaDeOutroCondominioEhRecusado() {
        var chamado = chamado(morador101, unidade101, "ABERTO");
        var deOutro = UUID.randomUUID();
        when(categoriaRepository.findByIdAndCondominioId(deOutro, condominio.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.atualizar(chamado.getId(), new AtualizacaoChamadoDTO(null, deOutro, null, null, null, null), auth(admin)));
        verify(chamadoRepository, never()).save(any());
    }
}
