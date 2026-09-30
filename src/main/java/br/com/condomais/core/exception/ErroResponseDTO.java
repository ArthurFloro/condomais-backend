package br.com.condomais.core.exception;

import java.time.Instant;

/**
 * Corpo padrão das respostas de erro da API.
 */
public record ErroResponseDTO(
        int status,
        String erro,
        String mensagem,
        String caminho,
        Instant timestamp
) {}
