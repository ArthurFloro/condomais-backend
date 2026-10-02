package br.com.condomais.assistente.dto;

import java.util.List;

/**
 * Conversa enviada pelo front a cada pergunta. A API é stateless: o histórico fica no navegador
 * e volta inteiro (o servidor só aproveita as mensagens mais recentes).
 */
public record ConversaAssistenteDTO(List<MensagemAssistenteDTO> mensagens) {}
