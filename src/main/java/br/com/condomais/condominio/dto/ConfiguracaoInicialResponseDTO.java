package br.com.condomais.condominio.dto;

import java.util.UUID;

/** Resultado do Setup Wizard: o front só mostra sucesso ao receber isto (201). */
public record ConfiguracaoInicialResponseDTO(
        UUID condominioId,
        String configuracaoInicial,
        int torres,
        int unidades,
        int areasComuns
) {}
