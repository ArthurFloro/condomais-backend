package br.com.condomais.interativo.dto;

import br.com.condomais.interativo.model.Aviso;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Comunicado como as telas veem: autor só pelo nome; torre nula = para o condomínio inteiro. */
public record AvisoResponseDTO(
        UUID id,
        String titulo,
        String conteudo,
        String prioridade,          // NORMAL ou URGENTE
        LocalDate dataValidade,     // nula = sem validade
        boolean ativo,              // falso depois da data de validade
        LocalDateTime criadoEm,
        String autorNome,
        UUID torreId,
        String torreNome
) {
    public static AvisoResponseDTO from(Aviso aviso, LocalDate hoje) {
        var torre = aviso.getTorre();
        return new AvisoResponseDTO(
                aviso.getId(),
                aviso.getTitulo(),
                aviso.getConteudo(),
                aviso.getPrioridade(),
                aviso.getDataValidade(),
                aviso.getDataValidade() == null || !aviso.getDataValidade().isBefore(hoje),
                aviso.getCreatedAt(),
                aviso.getAutor() != null ? aviso.getAutor().getNome() : null,
                torre != null ? torre.getId() : null,
                torre != null ? torre.getNome() : null
        );
    }
}
