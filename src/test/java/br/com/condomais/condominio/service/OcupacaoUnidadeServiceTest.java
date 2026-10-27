package br.com.condomais.condominio.service;

import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void inativaEReservadaSaoManuaisENaoMudam() {
        var inativa = unidade("INATIVO");
        var reservada = unidade("reservado");

        service.recalcular(inativa, reservada);

        assertEquals("INATIVO", inativa.getStatus());
        assertEquals("reservado", reservada.getStatus());
        verifyNoInteractions(usuarioRepository, apartamentoRepository);
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
