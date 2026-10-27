package br.com.condomais.atendimento.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Andamento do chamado pela administração. Campos nulos não mudam; o comentário vai para o histórico.
 * status: ABERTO, EM_ANALISE, EM_ATENDIMENTO, RESOLVIDO ou ENCERRADO. categoriaId recategoriza o chamado.
 */
public record AtualizacaoChamadoDTO(
        String status,
        UUID categoriaId,
        String comentario,
        String responsavelExterno,
        String contatoExterno,
        LocalDate dataPrevisaoAtendimento
) {}
