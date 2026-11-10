package br.com.condomais.condominio.service;

import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.core.exception.ConflitoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcupacaoUnidadeServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ApartamentoRepository apartamentoRepository;
    @InjectMocks private OcupacaoUnidadeService service;

    private static Apartamento unidade(String status) {
        var apartamento = new Apartamento();
        apartamento.setId(UUID.randomUUID());
        apartamento.setNumero("101");
        apartamento.setStatus(status);
        return apartamento;
    }

    @Test
    void unidadeLivreComMoradorAtivoFicaOcupada() {
        var apartamento = unidade("LIVRE");
        when(usuarioRepository.existeMoradorAtivoNaUnidade(apartamento.getId())).thenReturn(true);

        service.recalcular(apartamento);

        assertEquals("OCUPADO", apartamento.getStatus());
        verify(apartamentoRepository).save(apartamento);
    }

    @Test
    void unidadeOcupadaSemMoradorVoltaALivre() {
        var apartamento = unidade("OCUPADO");
        when(usuarioRepository.existeMoradorAtivoNaUnidade(apartamento.getId())).thenReturn(false);

        service.recalcular(apartamento);

        assertEquals("LIVRE", apartamento.getStatus());
        verify(apartamentoRepository).save(apartamento);
    }

    @Test
    void statusJaCertoNaoGravaNada() {
        var ocupada = unidade("OCUPADO");
        var livre = unidade("LIVRE");
        when(usuarioRepository.existeMoradorAtivoNaUnidade(ocupada.getId())).thenReturn(true);
        when(usuarioRepository.existeMoradorAtivoNaUnidade(livre.getId())).thenReturn(false);

        service.recalcular(ocupada, livre);

        assertEquals("OCUPADO", ocupada.getStatus());
        assertEquals("LIVRE", livre.getStatus());
        verify(apartamentoRepository, never()).save(any());
    }

    @Test
    void inativaEReservadaSemMoradorNaoMudam() {
        var inativa = unidade("INATIVO");
        var reservada = unidade("reservado");

        service.recalcular(inativa, reservada);

        assertEquals("INATIVO", inativa.getStatus());
        assertEquals("reservado", reservada.getStatus());
        verify(apartamentoRepository, never()).save(any());
    }

    @Test
    void moradorVinculadoAUnidadeReservadaOuInativaADeixaOcupada() {
        var reservada = unidade("RESERVADO");
        when(usuarioRepository.existeMoradorAtivoNaUnidade(reservada.getId())).thenReturn(true);

        service.recalcular(reservada);

        assertEquals("OCUPADO", reservada.getStatus());
    }

    // ---------------------------------------------------------------------
    // Status escolhido pelo admin
    // ---------------------------------------------------------------------

    @Test
    void adminNaoMarcaComoLivreUmaUnidadeComMorador() {
        var ocupada = unidade("OCUPADO");
        when(usuarioRepository.existeMoradorAtivoNaUnidade(ocupada.getId())).thenReturn(true);

        assertThrows(ConflitoException.class, () -> service.statusEscolhido(ocupada, "LIVRE"));
        assertThrows(ConflitoException.class, () -> service.statusEscolhido(ocupada, "INATIVO"));
        assertThrows(ConflitoException.class, () -> service.statusEscolhido(ocupada, "RESERVADO"));
        assertEquals("OCUPADO", service.statusEscolhido(ocupada, "ocupado"));
    }

    @Test
    void adminNaoMarcaComoOcupadaUmaUnidadeSemMorador() {
        var livre = unidade("LIVRE");
        when(usuarioRepository.existeMoradorAtivoNaUnidade(livre.getId())).thenReturn(false);

        assertThrows(ConflitoException.class, () -> service.statusEscolhido(livre, "OCUPADO"));
        assertEquals("RESERVADO", service.statusEscolhido(livre, "reservado"));
        assertEquals("INATIVO", service.statusEscolhido(livre, "INATIVO"));
    }

    @Test
    void unidadeNovaNaoNasceOcupada() {
        var nova = new Apartamento();

        assertEquals("LIVRE", service.statusEscolhido(nova, null));
        assertThrows(ConflitoException.class, () -> service.statusEscolhido(nova, "OCUPADO"));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void statusInventadoEhRecusadoEAusenteMantemOAtual() {
        var reservada = unidade("RESERVADO");

        assertThrows(IllegalArgumentException.class, () -> service.statusEscolhido(reservada, "DESOCUPADO"));
        assertEquals("RESERVADO", service.statusEscolhido(reservada, " "));
    }

    @Test
    void statusMinusculoOuAntigoTambemEhCorrigido() {
        var apartamento = unidade("livre");
        when(usuarioRepository.existeMoradorAtivoNaUnidade(apartamento.getId())).thenReturn(true);

        service.recalcular(apartamento);

        assertEquals("OCUPADO", apartamento.getStatus());
    }

    @Test
    void unidadeNulaOuRepetidaEhIgnorada() {
        var apartamento = unidade("LIVRE");
        when(usuarioRepository.existeMoradorAtivoNaUnidade(apartamento.getId())).thenReturn(true);

        service.recalcular(null, apartamento, apartamento);

        verify(usuarioRepository, times(1)).existeMoradorAtivoNaUnidade(apartamento.getId());
    }
}
