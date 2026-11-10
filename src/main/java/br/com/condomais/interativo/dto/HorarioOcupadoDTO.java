package br.com.condomais.interativo.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Horário já tomado numa área (agenda do dia). Não diz quem reservou. As datas mostram quando a reserva
 * vem do dia anterior ou segue para o seguinte (22h às 02h aparece nos dois dias).
 */
public record HorarioOcupadoDTO(LocalDate dataReserva, LocalTime horarioInicio, LocalDate dataFim, LocalTime horarioFim, String status) {}
