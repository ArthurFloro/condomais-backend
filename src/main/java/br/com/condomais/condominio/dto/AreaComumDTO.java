package br.com.condomais.condominio.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AreaComumDTO(
        String nome,
        String descricao,
        String regras,
        Boolean reservavel,
        Boolean exigeAprovacao,
        UUID condominioId,
        Integer capacidade,    // opcional
        BigDecimal taxa,       // opcional: nula ou zero = gratuita
        String instrucoesPagamento // opcional, só informativo
) {}