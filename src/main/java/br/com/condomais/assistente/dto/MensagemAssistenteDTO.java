package br.com.condomais.assistente.dto;

/** Uma fala da conversa. papel: USUARIO (morador) ou ASSISTENTE. */
public record MensagemAssistenteDTO(String papel, String texto) {}
