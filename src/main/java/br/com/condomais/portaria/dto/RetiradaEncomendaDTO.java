package br.com.condomais.portaria.dto;

import java.util.UUID;

/**
 * Retirada de encomenda. O nome de quem retira é obrigatório sempre. Na encomenda sem destinatário identificado,
 * a pessoa também informa o código (conferido com o cadastrado) e o apartamento dela (só registrado).
 */
public record RetiradaEncomendaDTO(
        String retiranteNome,
        String codigoConferencia,
        UUID apartamentoInformadoId
) {}
