package br.com.condomais.condominio.dto;

import java.util.List;

/**
 * Reexecução do Setup Wizard para acrescentar torres a um condomínio já configurado (CONDO-13.4). Só torres
 * novas, com os mesmos parâmetros da configuração inicial: nada que já existe é alterado ou apagado.
 */
public record AdicaoTorresDTO(List<ConfiguracaoInicialDTO.Torre> torres) {}
