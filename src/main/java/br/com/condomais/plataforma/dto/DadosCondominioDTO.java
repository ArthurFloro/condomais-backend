package br.com.condomais.plataforma.dto;

/** Identificação do condomínio, que só o MASTER cadastra e altera. */
public record DadosCondominioDTO(
        String nome,
        String cnpj
) {}
