package br.com.condomais.condominio.dto;

import br.com.condomais.condominio.model.AreaComum;

import java.util.UUID;

public record AreaComumResponseDTO(
        UUID id,
        String nome,
        String descricao,
        String regras,
        Boolean reservavel,
        Boolean exigeAprovacao,
        Boolean ativo,
        UUID condominioId
) {
    public static AreaComumResponseDTO from(AreaComum area) {
        return new AreaComumResponseDTO(
                area.getId(),
                area.getNome(),
                area.getDescricao(),
                area.getRegras(),
                area.getReservavel(),
                area.getExigeAprovacao(),
                area.getAtivo(),
                area.getCondominio().getId()
        );
    }
}
