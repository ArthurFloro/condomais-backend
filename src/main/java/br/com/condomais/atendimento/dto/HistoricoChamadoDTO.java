package br.com.condomais.atendimento.dto;

import br.com.condomais.atendimento.model.HistoricoChamado;

import java.time.LocalDateTime;

/** Uma linha do histórico do chamado (RF-CHA-012): o que aconteceu, quando e quem fez. */
public record HistoricoChamadoDTO(String acao, String comentario, LocalDateTime dataHora, String usuarioNome) {
    public static HistoricoChamadoDTO from(HistoricoChamado historico) {
        return new HistoricoChamadoDTO(historico.getAcao(), historico.getComentario(), historico.getDataHora(),
                historico.getUsuario() != null ? historico.getUsuario().getNome() : null);
    }
}
