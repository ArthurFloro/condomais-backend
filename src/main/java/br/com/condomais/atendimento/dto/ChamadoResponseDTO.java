package br.com.condomais.atendimento.dto;

import br.com.condomais.atendimento.model.Chamado;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Chamado como as telas veem. O histórico só vem no detalhe (nulo na listagem). */
public record ChamadoResponseDTO(
        UUID id,
        String titulo,
        String descricao,
        String prioridade,          // BAIXA, NORMAL, ALTA ou URGENTE
        String status,              // ABERTO, EM ANALISE, EM ATENDIMENTO, RESOLVIDO ou ENCERRADO
        UUID categoriaId,
        String categoriaNome,
        UUID apartamentoId,         // nulo = área comum ou assunto do condomínio
        String apartamentoNumero,
        String torreNome,
        String solicitanteNome,
        String imagemUrl,
        String responsavelExterno,
        String contatoExterno,
        LocalDate dataPrevisaoAtendimento,
        LocalDateTime dataAbertura,
        LocalDateTime dataEncerramento,
        List<HistoricoChamadoDTO> historico
) {
    public static ChamadoResponseDTO from(Chamado chamado, List<HistoricoChamadoDTO> historico) {
        var apartamento = chamado.getApartamento();
        var torre = apartamento != null ? apartamento.getTorre() : null;
        return new ChamadoResponseDTO(
                chamado.getId(), chamado.getTitulo(), chamado.getDescricao(), chamado.getPrioridade(), chamado.getStatus(),
                chamado.getCategoria().getId(), chamado.getCategoria().getNome(),
                apartamento != null ? apartamento.getId() : null,
                apartamento != null ? apartamento.getNumero() : null,
                torre != null ? torre.getNome() : null,
                chamado.getSolicitante() != null ? chamado.getSolicitante().getNome() : null,
                chamado.getImagemUrl(), chamado.getResponsavelExterno(), chamado.getContatoExterno(),
                chamado.getDataPrevisaoAtendimento(), chamado.getDataAbertura(), chamado.getDataEncerramento(),
                historico
        );
    }

    public static ChamadoResponseDTO from(Chamado chamado) {
        return from(chamado, null);
    }
}
