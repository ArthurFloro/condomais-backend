package br.com.condomais.auth.dto;

import br.com.condomais.auth.model.Usuario;

import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String nome,
        String cpf,
        String email,
        String telefone,
        String perfil,
        String status,
        String vinculo,
        UUID apartamentoId,
        String apartamentoNumero,
        String torreNome,
        boolean primeiroAcessoPendente,
        String condominioNome
) {
    public static UsuarioResponseDTO from(Usuario usuario) {
        var apartamento = usuario.getApartamento();
        var torre = apartamento != null ? apartamento.getTorre() : null;
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getCpf(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.getPerfil(),
                usuario.isAtivo() ? "ATIVO" : "INATIVO",
                usuario.getVinculo(),
                apartamento != null ? apartamento.getId() : null,
                apartamento != null ? apartamento.getNumero() : null,
                torre != null ? torre.getNome() : null,
                usuario.getSenha() == null,
                usuario.getCondominio() != null ? usuario.getCondominio().getNome() : null
        );
    }
}
