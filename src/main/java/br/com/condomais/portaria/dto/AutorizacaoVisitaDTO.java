package br.com.condomais.portaria.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Cadastro de uma visita com período de entrada autorizado.
 * apartamentoId, moradorId e autorizado só valem para portaria/administração: o morador autoriza visitas
 * para a própria unidade, e elas já nascem AUTORIZADO.
 */
public record AutorizacaoVisitaDTO(
        String cpf,
        String nome,
        String telefone,
        UUID apartamentoId,
        UUID moradorId,          // morador responsável (opcional), da mesma unidade
        LocalDate dataInicio,
        LocalTime horaInicio,
        LocalDate dataFim,
        LocalTime horaFim,
        String observacao,
        Boolean autorizado       // portaria: true quando já há autorização prévia; senão fica PENDENTE
) {}
