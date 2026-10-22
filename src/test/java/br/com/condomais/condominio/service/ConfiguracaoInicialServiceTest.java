package br.com.condomais.condominio.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.condominio.dto.ConfiguracaoInicialDTO;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.AreaComum;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.condominio.repository.AreaComumRepository;
import br.com.condomais.condominio.repository.CondominioRepository;
import br.com.condomais.condominio.repository.TorreRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfiguracaoInicialServiceTest {

    @Mock private CondominioRepository condominioRepository;
    @Mock private TorreRepository torreRepository;
    @Mock private ApartamentoRepository apartamentoRepository;
    @Mock private AreaComumRepository areaComumRepository;
    @InjectMocks private ConfiguracaoInicialService service;

    private Condominio condominio;
    private UsuarioAutenticado admin;
    private final List<Apartamento> unidadesGravadas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        condominio.setStatus("ATIVO");
        condominio.setConfiguracaoInicial("PENDENTE");
        admin = autenticado("ADMIN");
        when(condominioRepository.findParaConfiguracao(condominio.getId())).thenReturn(Optional.of(condominio));
        when(apartamentoRepository.saveAll(anyList())).thenAnswer(i -> {
            List<Apartamento> lote = i.getArgument(0);
            unidadesGravadas.addAll(lote);
            return lote;
        });
    }

    private UsuarioAutenticado autenticado(String perfil) {
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setPerfil(perfil);
        usuario.setCondominio(condominio);
        return new UsuarioAutenticado(usuario);
    }

    private static ConfiguracaoInicialDTO.Torre torre(String nome, int andares, int porAndar, int digitos, String separador) {
        return new ConfiguracaoInicialDTO.Torre(nome, andares, porAndar, 1, 1, digitos, separador);
    }

    private static ConfiguracaoInicialDTO dados(List<ConfiguracaoInicialDTO.Torre> torres, List<ConfiguracaoInicialDTO.AreaComum> areas) {
        return new ConfiguracaoInicialDTO("VERTICAL", "Rua das Flores, 100", "Recife", "pe", torres, areas);
    }

    @Test
    void geraAsUnidadesDoExemploDaEspecificacaoEConclui() {
        // 3 torres x 10 andares x 4 por andar = 120 unidades (§5.2 da especificação)
        var torres = List.of(torre("A", 10, 4, 2, ""), torre("B", 10, 4, 2, ""), torre("C", 10, 4, 2, ""));
        var areas = List.of(new ConfiguracaoInicialDTO.AreaComum("Piscina", null, false, false));

        var resposta = service.configurar(dados(torres, areas), admin);

        assertEquals(3, resposta.torres());
        assertEquals(120, resposta.unidades());
        assertEquals(1, resposta.areasComuns());
        assertEquals("CONCLUIDA", resposta.configuracaoInicial());
        assertEquals("CONCLUIDA", condominio.getConfiguracaoInicial());
        assertEquals("PE", condominio.getEstado());
        var numerosTorreA = unidadesGravadas.subList(0, 40).stream().map(Apartamento::getNumero).toList();
        assertEquals(List.of("101", "102", "103", "104"), numerosTorreA.subList(0, 4));
        assertEquals("1004", numerosTorreA.get(39));
        assertTrue(unidadesGravadas.stream().allMatch(a -> "LIVRE".equals(a.getStatus()) && a.getCondominio() == condominio));
    }

    @Test
    void separadorEDigitosEntramNoNumero() {
        service.configurar(dados(List.of(new ConfiguracaoInicialDTO.Torre("A", 2, 2, 5, 3, 3, "-")), null), admin);

        assertEquals(List.of("5-003", "5-004", "6-003", "6-004"), unidadesGravadas.stream().map(Apartamento::getNumero).toList());
    }

    @Test
    void numeracaoQueSeRepeteNaTorreEhRecusadaSemGravar() {
        // Sem separador e com 1 dígito: 1º andar unidade 11 e 11º andar unidade 1 viram os dois "111"
        var erro = assertThrows(IllegalArgumentException.class,
                () -> service.configurar(dados(List.of(torre("A", 11, 11, 1, "")), null), admin));

        assertTrue(erro.getMessage().contains("\"111\""));
        verify(torreRepository, never()).save(any());
        verify(apartamentoRepository, never()).saveAll(anyList());
        assertEquals("PENDENTE", condominio.getConfiguracaoInicial());
    }

    @Test
    void segundoEnvioDepoisDeConcluidoEhConflito() {
        condominio.setConfiguracaoInicial("CONCLUIDA");

        assertThrows(ConflitoException.class,
                () -> service.configurar(dados(List.of(torre("A", 1, 1, 2, "")), null), admin));
        verifyNoInteractions(torreRepository, apartamentoRepository, areaComumRepository);
    }

    @Test
    void torreRepetidaNoEnvioIgnorandoMaiusculasEhRecusada() {
        assertThrows(IllegalArgumentException.class,
                () -> service.configurar(dados(List.of(torre("Torre A", 1, 1, 2, ""), torre("torre a", 1, 1, 2, "")), null), admin));
        verify(torreRepository, never()).save(any());
    }

    @Test
    void torreQueJaExisteNoCondominioEhConflito() {
        when(torreRepository.existsByCondominioIdAndNomeIgnoreCase(condominio.getId(), "Torre A")).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> service.configurar(dados(List.of(torre("Torre A", 1, 1, 2, "")), null), admin));
    }

    @Test
    void limitesProvisoriosSaoAplicados() {
        assertThrows(IllegalArgumentException.class,
                () -> service.configurar(dados(List.of(torre("A", 101, 1, 2, "")), null), admin));
        assertThrows(IllegalArgumentException.class,
                () -> service.configurar(dados(List.of(torre("A", 1, 0, 2, "")), null), admin));
        assertThrows(IllegalArgumentException.class,
                () -> service.configurar(dados(List.of(torre("A", 1, 1, 5, "")), null), admin));
        // 51 torres de 100 unidades = 5.100, acima do teto de 5.000
        var muitas = new ArrayList<ConfiguracaoInicialDTO.Torre>();
        for (int i = 0; i < 51; i++) muitas.add(torre("T" + i, 10, 10, 2, ""));
        assertThrows(IllegalArgumentException.class, () -> service.configurar(dados(muitas, null), admin));
        verify(apartamentoRepository, never()).saveAll(anyList());
    }

    @Test
    void enderecoEUfSaoObrigatorios() {
        var semEndereco = new ConfiguracaoInicialDTO("VERTICAL", " ", "Recife", "PE", List.of(torre("A", 1, 1, 2, "")), null);
        var ufInvalida = new ConfiguracaoInicialDTO("VERTICAL", "Rua X", "Recife", "XX", List.of(torre("A", 1, 1, 2, "")), null);

        assertThrows(IllegalArgumentException.class, () -> service.configurar(semEndereco, admin));
        assertThrows(IllegalArgumentException.class, () -> service.configurar(ufInvalida, admin));
    }

    @Test
    void outrosModelosAindaNaoEstaoDisponiveis() {
        var horizontal = new ConfiguracaoInicialDTO("HORIZONTAL", "Rua X", "Recife", "PE", List.of(torre("Rua A", 1, 1, 2, "")), null);

        assertThrows(IllegalArgumentException.class, () -> service.configurar(horizontal, admin));
    }

    @Test
    void areasRespeitamOQueOAdminEscolheu() {
        var areas = List.of(
                new ConfiguracaoInicialDTO.AreaComum("Salão de festas", "Até 22h", true, true),
                new ConfiguracaoInicialDTO.AreaComum("Playground", null, null, null));

        service.configurar(dados(List.of(torre("A", 1, 1, 2, "")), areas), admin);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AreaComum>> gravadas = ArgumentCaptor.forClass(List.class);
        verify(areaComumRepository).saveAll(gravadas.capture());
        var salao = gravadas.getValue().get(0);
        var playground = gravadas.getValue().get(1);
        assertTrue(salao.getReservavel());
        assertTrue(salao.getExigeAprovacao());
        assertFalse(playground.getReservavel());
        assertFalse(playground.getExigeAprovacao());
    }

    @Test
    void areaRepetidaNoEnvioEhRecusada() {
        var areas = List.of(new ConfiguracaoInicialDTO.AreaComum("Piscina", null, false, false),
                new ConfiguracaoInicialDTO.AreaComum("piscina", null, false, false));

        assertThrows(IllegalArgumentException.class,
                () -> service.configurar(dados(List.of(torre("A", 1, 1, 2, "")), areas), admin));
    }

    @Test
    void soOAdminConfigura() {
        for (var perfil : List.of("MORADOR", "PORTARIA")) {
            assertThrows(SecurityException.class,
                    () -> service.configurar(dados(List.of(torre("A", 1, 1, 2, "")), null), autenticado(perfil)));
        }
        verifyNoInteractions(torreRepository, apartamentoRepository, areaComumRepository);
    }
}
