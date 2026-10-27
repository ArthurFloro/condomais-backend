package br.com.condomais.interativo.dto;

import java.time.LocalTime;

/** Horário já tomado numa área (agenda do dia). Não diz quem reservou. */
public record HorarioOcupadoDTO(LocalTime horarioInicio, LocalTime horarioFim, String status) {}
