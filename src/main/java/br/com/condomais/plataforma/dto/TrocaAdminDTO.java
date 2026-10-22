package br.com.condomais.plataforma.dto;

import java.util.UUID;

/**
 * Troca do administrador de um condomínio. Com usuarioId, promove um usuário que já é do condomínio;
 * sem ele, cria um administrador novo com os demais campos. Os administradores anteriores são desativados.
 */
public record TrocaAdminDTO(
        UUID usuarioId,
        String nome,
        String cpf,
        String email,
        String telefone
) {}
