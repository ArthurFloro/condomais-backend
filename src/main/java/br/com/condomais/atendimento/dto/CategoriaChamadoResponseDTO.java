package br.com.condomais.atendimento.dto;

import br.com.condomais.atendimento.model.CategoriaChamado;

import java.util.UUID;

public record CategoriaChamadoResponseDTO(UUID id, String nome) {
    public static CategoriaChamadoResponseDTO from(CategoriaChamado categoria) {
        return new CategoriaChamadoResponseDTO(categoria.getId(), categoria.getNome());
    }
}
