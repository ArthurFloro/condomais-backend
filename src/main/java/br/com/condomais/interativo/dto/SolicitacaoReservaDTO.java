package br.com.condomais.interativo.dto;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** dataFim é opcional: sem ela, a reserva termina no mesmo dia; com o dia seguinte, passa da meia-noite. */
public record SolicitacaoReservaDTO(UUID areaId, LocalDate dataReserva, LocalTime horarioInicio, LocalTime horarioFim, LocalDate dataFim) {}
