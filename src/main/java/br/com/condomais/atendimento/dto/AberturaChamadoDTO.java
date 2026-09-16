package br.com.condomais.atendimento.dto;
import java.util.UUID;

public record AberturaChamadoDTO(
        String titulo,
        String descricao,
        UUID categoriaId,
        UUID apartamentoId,
        String prioridade,
        String imagemUrl
) {}