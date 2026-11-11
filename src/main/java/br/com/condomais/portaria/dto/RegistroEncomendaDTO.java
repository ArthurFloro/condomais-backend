package br.com.condomais.portaria.dto;

import java.util.UUID;

public record RegistroEncomendaDTO(
        UUID apartamentoId,           // nulo = destinatário não identificado
        String codigoIdentificacao,
        String observacao
) {
}
