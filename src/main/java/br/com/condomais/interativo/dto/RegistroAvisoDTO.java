package br.com.condomais.interativo.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RegistroAvisoDTO(
        String titulo,
        String conteudo,
        String prioridade,
        LocalDate dataValidade,
        UUID torreId // Se for nulo, o aviso vai para todo o condomínio
) {}