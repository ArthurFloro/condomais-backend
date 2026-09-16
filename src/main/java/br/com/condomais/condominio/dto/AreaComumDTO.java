package br.com.condomais.condominio.dto;

import java.util.UUID;

public record AreaComumDTO(
        String nome,
        String descricao,
        String regras,
        Boolean reservavel,
        Boolean exigeAprovacao,
        UUID condominioId
) {}