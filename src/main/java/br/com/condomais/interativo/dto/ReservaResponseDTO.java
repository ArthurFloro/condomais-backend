package br.com.condomais.interativo.dto;

import br.com.condomais.interativo.model.Reserva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Reserva como as telas veem: área, horário, unidade e nomes, sem dados pessoais. */
public record ReservaResponseDTO(
        UUID id,
        UUID areaId,
        String areaNome,
        LocalDate dataReserva,
        LocalTime horarioInicio,
        LocalTime horarioFim,
        LocalDate dataFim,          // dia seguinte quando a reserva passa da meia-noite
        String status,             // CONFIRMADA, AGUARDANDO APROVACAO, CANCELADA ou RECUSADA
        String moradorNome,
        String apartamentoNumero,
        String torreNome,
        String aprovadorNome,       // quem aprovou ou recusou, quando houve aprovação
        BigDecimal valorTaxa,       // taxa cobrada (nula = gratuita)
        String instrucoesPagamento, // da área, para o morador saber como pagar
        String pagamentoConfirmadoPorNome,
        LocalDateTime pagamentoConfirmadoEm
) {
    public static ReservaResponseDTO from(Reserva reserva) {
        var morador = reserva.getMorador();
        var apartamento = morador != null ? morador.getApartamento() : null;
        var torre = apartamento != null ? apartamento.getTorre() : null;
        return new ReservaResponseDTO(
                reserva.getId(),
                reserva.getArea().getId(),
                reserva.getArea().getNome(),
                reserva.getDataReserva(),
                reserva.getHorarioInicio(),
                reserva.getHorarioFim(),
                reserva.getDataFim() != null ? reserva.getDataFim() : reserva.getDataReserva(),
                reserva.getStatus(),
                morador != null ? morador.getNome() : null,
                apartamento != null ? apartamento.getNumero() : null,
                torre != null ? torre.getNome() : null,
                reserva.getAprovador() != null ? reserva.getAprovador().getNome() : null,
                reserva.getValorTaxa(),
                reserva.getValorTaxa() != null ? reserva.getArea().getInstrucoesPagamento() : null,
                reserva.getPagamentoConfirmadoPor() != null ? reserva.getPagamentoConfirmadoPor().getNome() : null,
                reserva.getPagamentoConfirmadoEm()
        );
    }
}
