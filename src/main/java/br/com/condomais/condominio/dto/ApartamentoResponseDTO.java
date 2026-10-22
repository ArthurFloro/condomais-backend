package br.com.condomais.condominio.dto;

import br.com.condomais.condominio.model.Apartamento;

import java.util.UUID;

public record ApartamentoResponseDTO(
        UUID id,
        String numero,
        String status,
        UUID torreId,
        UUID condominioId,
        Integer andar
) {
    public static ApartamentoResponseDTO from(Apartamento apartamento) {
        return new ApartamentoResponseDTO(
                apartamento.getId(),
                apartamento.getNumero(),
                apartamento.getStatus(),
                apartamento.getTorre() != null ? apartamento.getTorre().getId() : null,
                apartamento.getCondominio().getId(),
                apartamento.getAndar()
        );
    }
}
