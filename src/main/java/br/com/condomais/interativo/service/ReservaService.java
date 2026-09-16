package br.com.condomais.interativo.service;

import br.com.condomais.auth.repository.UsuarioApartamentoRepository;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.repository.AreaComumRepository;
import br.com.condomais.interativo.dto.SolicitacaoReservaDTO;
import br.com.condomais.interativo.model.Reserva;
import br.com.condomais.interativo.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ReservaService {

    @Autowired private ReservaRepository reservaRepository;
    @Autowired private AreaComumRepository areaComumRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioApartamentoRepository vinculoRepository;

    @Transactional
    public Reserva solicitarReserva(SolicitacaoReservaDTO dados, UUID moradorId, UUID condominioId) {
        var area = areaComumRepository.findByIdAndCondominioId(dados.areaId(), condominioId)
                .orElseThrow(() -> new IllegalArgumentException("Área comum não encontrada."));

        if (!area.getReservavel()) {
            throw new IllegalArgumentException("Esta área não está configurada como reservável (RN-RES-001).");
        }

        if (reservaRepository.existeConflitoDeHorario(area.getId(), dados.dataReserva(), dados.horarioInicio(), dados.horarioFim())) {
            throw new IllegalStateException("Horário indisponível. Já existe uma reserva conflitante (RN-RES-002).");
        }

        var morador = usuarioRepository.findById(moradorId).orElseThrow();
        var reserva = new Reserva();
        reserva.setArea(area);
        reserva.setMorador(morador);
        reserva.setCondominio(area.getCondominio());
        reserva.setDataReserva(dados.dataReserva());
        reserva.setHorarioInicio(dados.horarioInicio());
        reserva.setHorarioFim(dados.horarioFim());

        // Regra Especial da V1.1 (Ex: Salão de Festas exige aprovação de proprietário se for inquilino)
        if (area.getExigeAprovacao()) {
            boolean isProprietario = vinculoRepository.findByUsuarioId(moradorId).stream()
                    .anyMatch(v -> v.getTipoVinculo().equalsIgnoreCase("Proprietário"));

            if (isProprietario) {
                reserva.setStatus("CONFIRMADA");
            } else {
                reserva.setStatus("AGUARDANDO APROVACAO");
                // TODO: Disparar notificação para o proprietário da unidade avaliar a solicitação
            }
        } else {
            reserva.setStatus("CONFIRMADA");
        }

        return reservaRepository.save(reserva);
    }
}