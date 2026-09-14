package br.com.condomais.portaria.dto;

import java.util.UUID;

public record RegistroEncomendaDTO(
        UUID apartamentoId,
        String codigoIdentificacao,
        String observacao
) {
}
