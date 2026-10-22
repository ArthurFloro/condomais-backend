package br.com.condomais.plataforma.dto;

/** Cadastro feito pelo MASTER: o condomínio e o primeiro administrador, gravados juntos. */
public record CadastroCondominioDTO(
        DadosCondominioDTO condominio,
        DadosAdminDTO admin
) {}
