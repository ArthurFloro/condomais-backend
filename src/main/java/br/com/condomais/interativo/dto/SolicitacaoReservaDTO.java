package br.com.condomais.interativo.dto;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SolicitacaoReservaDTO(UUID areaId, LocalDate dataReserva, LocalTime horarioInicio, LocalTime horarioFim) {}