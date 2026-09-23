package br.com.condomais.condominio.dto;

import br.com.condomais.condominio.model.Torre;

import java.util.UUID;

public record TorreResponseDTO(
        UUID id,
        String nome,
        UUID condominioId
) {
    public static TorreResponseDTO from(Torre torre) {
        return new TorreResponseDTO(torre.getId(), torre.getNome(), torre.getCondominio().getId());
    }
}
