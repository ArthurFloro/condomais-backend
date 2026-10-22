package br.com.condomais.interativo.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioApartamentoRepository;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.AreaComum;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.repository.AreaComumRepository;
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
    @Mock private UsuarioApartamentoRepository vinculoRepository;
    @InjectMocks private ReservaService service;

    private Condominio condominio;
    private AreaComum area;
    private SolicitacaoReservaDTO solicitacao;

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        area = new AreaComum();
        area.setId(UUID.randomUUID());
        area.setNome("Salão de festas");
        area.setReservavel(true);
        area.setExigeAprovacao(false);
        area.setCondominio(condominio);
        solicitacao = new SolicitacaoReservaDTO(area.getId(), LocalDate.now().plusDays(7), LocalTime.of(14, 0), LocalTime.of(18, 0));
        when(areaComumRepository.findByIdAndCondominioId(area.getId(), condominio.getId())).thenReturn(Optional.of(area));
    }

    @Test
    void areaDesativadaNaoAceitaReservaNova() {
        area.setAtivo(false);

        var erro = assertThrows(IllegalArgumentException.class,
                () -> service.solicitarReserva(solicitacao, UUID.randomUUID(), condominio.getId()));

        assertTrue(erro.getMessage().contains("desativada"));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void areaAtivaEReservavelContinuaAceitandoReserva() {
        var moradorId = UUID.randomUUID();
        when(reservaRepository.existeConflitoDeHorario(any(), any(), any(), any())).thenReturn(false);
        when(usuarioRepository.findById(moradorId)).thenReturn(Optional.of(new Usuario()));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));

        var reserva = service.solicitarReserva(solicitacao, moradorId, condominio.getId());

        assertEquals("CONFIRMADA", reserva.getStatus());
        assertSame(area, reserva.getArea());
    }
}
