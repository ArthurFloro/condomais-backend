package br.com.condomais.condominio.dto;

import java.util.UUID;

public record TorreDTO(
        String nome,
        UUID condominioId
) {}