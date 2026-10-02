package br.com.condomais.assistente.service;

import br.com.condomais.atendimento.repository.ChamadoRepository;
import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.model.Torre;
import br.com.condomais.condominio.repository.AreaComumRepository;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.repository.AvisoRepository;
import br.com.condomais.interativo.repository.ReservaRepository;
import br.com.condomais.portaria.model.Encomenda;
import br.com.condomais.portaria.repository.EncomendaRepository;
import br.com.condomais.portaria.repository.VisitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FerramentasAssistenteTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EncomendaRepository encomendaRepository;
    @Mock private AvisoRepository avisoRepository;
    @Mock private ReservaRepository reservaRepository;
    @Mock private VisitaRepository visitaRepository;
    @Mock private ChamadoRepository chamadoRepository;
    @Mock private AreaComumRepository areaComumRepository;
    @InjectMocks private FerramentasAssistente ferramentas;

    private Condominio condominio;
    private Apartamento apartamento;
    private Usuario morador;
    private UsuarioAutenticado auth;

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        condominio.setNome("Residencial Teste");
        var torre = new Torre();
        torre.setId(UUID.randomUUID());
        torre.setNome("A");
        apartamento = new Apartamento();
        apartamento.setId(UUID.randomUUID());
        apartamento.setNumero("101");
        apartamento.setTorre(torre);
        morador = new Usuario();
        morador.setId(UUID.randomUUID());
        morador.setNome("Maria");
        morador.setPerfil("MORADOR");
        morador.setVinculo("PROPRIETARIO");
        morador.setCondominio(condominio);
        morador.setApartamento(apartamento);
        auth = new UsuarioAutenticado(morador);
        lenient().when(usuarioRepository.findByIdAndCondominioId(morador.getId(), condominio.getId())).thenReturn(Optional.of(morador));
    }

    private Encomenda encomenda(String codigo, String status) {
        var encomenda = new Encomenda();
        encomenda.setCodigoIdentificacao(codigo);
        encomenda.setStatus(status);
        encomenda.setDataHoraRecebimento(LocalDateTime.of(2026, 10, 1, 14, 30));
        return encomenda;
    }

    @Test
    void encomendasUsamSempreAUnidadeDoTokenEFiltramAsPendentes() {
        when(encomendaRepository.findTop20ByCondominioIdAndApartamentoIdOrderByDataHoraRecebimentoDesc(condominio.getId(), apartamento.getId()))
                .thenReturn(List.of(encomenda("BR123", "AGUARDANDO RETIRADA"), encomenda("BR999", "RETIRADA")));

        var pendentes = ferramentas.executar(FerramentasAssistente.ENCOMENDAS, Map.of("situacao", "PENDENTES"), auth);
        assertTrue(pendentes.contains("BR123"));
        assertTrue(pendentes.contains("01/10/2026 14:30"));
        assertFalse(pendentes.contains("BR999"));

        var todas = ferramentas.executar(FerramentasAssistente.ENCOMENDAS, Map.of("situacao", "TODAS"), auth);
        assertTrue(todas.contains("BR999"));
    }

    @Test
    void entradaDoModeloNaoTrocaOMorador() {
        // Campos extras (ex.: uma unidade inventada pelo modelo) não alteram o escopo da consulta
        when(encomendaRepository.findTop20ByCondominioIdAndApartamentoIdOrderByDataHoraRecebimentoDesc(any(), any())).thenReturn(List.of());
        ferramentas.executar(FerramentasAssistente.ENCOMENDAS, Map.of("situacao", "TODAS", "apartamentoId", UUID.randomUUID().toString()), auth);
        verify(encomendaRepository).findTop20ByCondominioIdAndApartamentoIdOrderByDataHoraRecebimentoDesc(condominio.getId(), apartamento.getId());
    }

    @Test
    void moradorSemUnidadeRecebeOrientacaoSemConsultarEncomendas() {
        morador.setApartamento(null);
        var resultado = ferramentas.executar(FerramentasAssistente.ENCOMENDAS, Map.of("situacao", "PENDENTES"), auth);
        assertTrue(resultado.contains("não tem unidade vinculada"));
        verifyNoInteractions(encomendaRepository);
    }

    @Test
    void reservasSaoBuscadasPeloIdDoMorador() {
        when(reservaRepository.findTop20ByCondominioIdAndMoradorIdOrderByDataReservaDescHorarioInicioDesc(condominio.getId(), morador.getId())).thenReturn(List.of());
        assertEquals("[]", ferramentas.executar(FerramentasAssistente.RESERVAS, Map.of("periodo", "PROXIMAS"), auth));
    }

    @Test
    void ferramentaOuOpcaoInvalidaViramErroDeEntrada() {
        assertThrows(IllegalArgumentException.class, () -> ferramentas.executar("apagar_tudo", Map.of(), auth));
        assertThrows(IllegalArgumentException.class, () -> ferramentas.executar(FerramentasAssistente.CHAMADOS, Map.of("situacao", "QUALQUER"), auth));
    }

    @Test
    void contextoTrazNomeUnidadeETorre() {
        var contexto = ferramentas.contexto(auth);
        assertEquals("Maria", contexto.nome());
        assertEquals("Residencial Teste", contexto.condominio());
        assertEquals("101", contexto.unidade());
        assertEquals("A", contexto.torre());
    }

    @Test
    void definicoesSaoEstritasESomenteDeConsulta() {
        var nomes = ferramentas.definicoes().stream().map(tool -> tool.name()).toList();
        assertEquals(List.of("consultar_encomendas", "consultar_avisos", "consultar_reservas", "consultar_visitas", "consultar_chamados", "listar_areas_comuns"), nomes);
        assertTrue(ferramentas.definicoes().stream().allMatch(tool -> tool.strict().orElse(false)));
    }
}
