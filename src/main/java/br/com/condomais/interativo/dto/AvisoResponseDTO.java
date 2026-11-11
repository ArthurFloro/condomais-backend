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
        String torreNome,
        UUID chamadoId,             // chamado relacionado, quando há: só código, título e status, sem descrição nem histórico
        String chamadoCodigo,       // o mesmo código das telas (#3F2A1C9E), usado nas menções @3F2A1C9E
        String chamadoTitulo,
        String chamadoStatus
) {
    /** Código curto de um chamado nas telas: os 8 primeiros caracteres do id. */
    public static String codigoDoChamado(UUID id) {
        return "#" + id.toString().substring(0, 8).toUpperCase();
    }

    public static AvisoResponseDTO from(Aviso aviso, LocalDate hoje) {
        var torre = aviso.getTorre();
        var chamado = aviso.getChamado();
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
                torre != null ? torre.getNome() : null,
                chamado != null ? chamado.getId() : null,
                chamado != null ? codigoDoChamado(chamado.getId()) : null,
                chamado != null ? chamado.getTitulo() : null,
                chamado != null ? chamado.getStatus() : null
        );
    }
}
