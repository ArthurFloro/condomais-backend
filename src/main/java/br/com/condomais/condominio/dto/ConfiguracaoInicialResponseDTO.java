package br.com.condomais.condominio.dto;

import java.util.UUID;

/** Resultado do Setup Wizard: o front só mostra sucesso ao receber isto (201). */
public record ConfiguracaoInicialResponseDTO(
        UUID condominioId,
        String configuracaoInicial,
        String modelo,
        int torres,        // torres no VERTICAL, ruas no HORIZONTAL, 0 no EDIFICIO
        int unidades,
        int areasComuns
) {}
