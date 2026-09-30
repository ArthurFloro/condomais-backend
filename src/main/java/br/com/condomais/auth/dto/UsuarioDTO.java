package br.com.condomais.auth.dto;

import java.util.UUID;

/**
 * Cadastro/edição de usuário pela Administração. Não recebe senha: o próprio usuário
 * a define no primeiro acesso. O condomínio vem do token de quem cadastra.
 */
public record UsuarioDTO(
        String nome,
        String cpf,
        String email,
        String telefone,
        String perfil,        // ADMIN, PORTARIA, MORADOR
        UUID apartamentoId,   // obrigatório para MORADOR
        String vinculo        // PROPRIETARIO, INQUILINO (obrigatório para MORADOR)
) {}
