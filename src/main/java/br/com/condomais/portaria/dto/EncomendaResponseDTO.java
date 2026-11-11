package br.com.condomais.portaria.dto;

import br.com.condomais.portaria.model.Encomenda;

import java.time.LocalDateTime;
import java.util.UUID;

/** Encomenda como as telas veem: unidade e nomes de quem recebeu e entregou, sem dados pessoais. */
public record EncomendaResponseDTO(
        UUID id,
        String codigoIdentificacao,
        String observacao,
        String status,              // AGUARDANDO RETIRADA ou RETIRADA
        LocalDateTime dataHoraRecebimento,
        LocalDateTime dataHoraRetirada,
        UUID apartamentoId,
        String apartamentoNumero,
        String torreNome,
        String moradorNome,         // destinatário: morador da unidade no momento do recebimento
        String porteiroRecebimentoNome,
        String porteiroRetiradaNome,
        boolean semDestinatario,    // chegou sem unidade identificada
        String retiranteNome,       // quem retirou
        String retiranteApartamentoNumero, // apartamento que o retirante informou (encomenda sem unidade)
        String retiranteTorreNome
) {
    public static EncomendaResponseDTO from(Encomenda encomenda) {
        var apartamento = encomenda.getApartamento();
        var torre = apartamento != null ? apartamento.getTorre() : null;
        var informado = encomenda.getRetiranteApartamento();
        var torreInformada = informado != null ? informado.getTorre() : null;
        return new EncomendaResponseDTO(
                encomenda.getId(),
                encomenda.getCodigoIdentificacao(),
                encomenda.getObservacao(),
                encomenda.getStatus(),
                encomenda.getDataHoraRecebimento(),
                encomenda.getDataHoraRetirada(),
                apartamento != null ? apartamento.getId() : null,
                apartamento != null ? apartamento.getNumero() : null,
                torre != null ? torre.getNome() : null,
                encomenda.getMorador() != null ? encomenda.getMorador().getNome() : null,
                encomenda.getPorteiroRecebimento() != null ? encomenda.getPorteiroRecebimento().getNome() : null,
                encomenda.getPorteiroRetirada() != null ? encomenda.getPorteiroRetirada().getNome() : null,
                apartamento == null,
                encomenda.getRetiranteNome(),
                informado != null ? informado.getNumero() : null,
                torreInformada != null ? torreInformada.getNome() : null
        );
    }
}
