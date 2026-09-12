package br.com.condomais.portaria.dto;

import java.util.UUID;

public record RegistroEntradaDTO(
        String cpf,
        String nome,
        String telefone,
        UUID apartamentoId,
        String observacao
) {}