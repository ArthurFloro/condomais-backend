package br.com.condomais.portaria.dto;

import br.com.condomais.portaria.model.Visita;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Visita como as telas veem. status é o efetivo: PENDENTE ou AUTORIZADO com o período vencido aparece como EXPIRADO. */
public record VisitaResponseDTO(
        UUID id,
        String status,              // PENDENTE, AUTORIZADO, RECUSADO, ENTROU, SAIU ou EXPIRADO
        String origem,              // PORTARIA ou MORADOR
        String visitanteNome,
        String visitanteCpf,
        String visitanteTelefone,
        UUID apartamentoId,
        String apartamentoNumero,
        String torreNome,
        String moradorNome,
        LocalDate dataInicio,
        LocalTime horaInicio,
        LocalDate dataFim,
        LocalTime horaFim,
        String observacao,
        LocalDateTime dataHoraRegistro,
        LocalDateTime dataHoraEntrada,
        LocalDateTime dataHoraSaida,
        String porteiroEntradaNome,
        String porteiroSaidaNome
) {
    public static VisitaResponseDTO from(Visita visita, String statusEfetivo) {
        var visitante = visita.getVisitante();
        var apartamento = visita.getApartamento();
        var torre = apartamento.getTorre();
        return new VisitaResponseDTO(
                visita.getId(),
                statusEfetivo,
                visita.getOrigem(),
                visitante.getNome(),
                visitante.getCpf(),
                visitante.getTelefone(),
                apartamento.getId(),
                apartamento.getNumero(),
                torre != null ? torre.getNome() : null,
                visita.getMorador() != null ? visita.getMorador().getNome() : null,
                visita.getDataPrevista(),
                visita.getHorarioPrevisto(),
                visita.getDataFimPrevista(),
                visita.getHorarioFimPrevisto(),
                visita.getObservacao(),
                visita.getDataHoraRegistro(),
                visita.getDataHoraEntrada(),
                visita.getDataHoraSaida(),
                visita.getPorteiroEntrada() != null ? visita.getPorteiroEntrada().getNome() : null,
                visita.getPorteiroSaida() != null ? visita.getPorteiroSaida().getNome() : null
        );
    }
}
