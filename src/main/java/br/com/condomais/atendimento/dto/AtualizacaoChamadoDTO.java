package br.com.condomais.atendimento.dto;

import java.time.LocalDate;

/**
 * Andamento do chamado pela administração. Campos nulos não mudam; o comentário vai para o histórico.
 * status: ABERTO, EM_ANALISE, EM_ATENDIMENTO, RESOLVIDO ou ENCERRADO.
 */
public record AtualizacaoChamadoDTO(
        String status,
        String comentario,
        String responsavelExterno,
        String contatoExterno,
        LocalDate dataPrevisaoAtendimento
) {}
