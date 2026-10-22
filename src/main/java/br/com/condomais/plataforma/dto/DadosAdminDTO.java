package br.com.condomais.plataforma.dto;

/** Administrador criado pelo MASTER, sem senha: ele a define em /auth/primeiro-acesso. */
public record DadosAdminDTO(
        String nome,
        String cpf,
        String email,
        String telefone
) {}
