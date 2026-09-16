package br.com.condomais.condominio.dto;

import java.util.UUID;

public record ApartamentoDTO(
        String numero,
        String status,
        UUID torreId,
        UUID condominioId
) {}