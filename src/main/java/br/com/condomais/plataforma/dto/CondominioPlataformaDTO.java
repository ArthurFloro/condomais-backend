package br.com.condomais.plataforma.dto;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.condominio.model.Condominio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Condomínio como o MASTER o vê: dados cadastrais e quem o administra, sem os dados internos. */
public record CondominioPlataformaDTO(
        UUID id,
        String nome,
        String cnpj,
        String status,
        String configuracaoInicial,
        String modelo,
        LocalDateTime criadoEm,
        List<Administrador> administradores
) {
    public record Administrador(
            UUID id,
            String nome,
            String cpf,
            String email,
            String status,
            boolean primeiroAcessoPendente
    ) {
        static Administrador from(Usuario usuario) {
            return new Administrador(usuario.getId(), usuario.getNome(), usuario.getCpf(), usuario.getEmail(),
                    usuario.isAtivo() ? "ATIVO" : "INATIVO", usuario.getSenha() == null);
        }
    }

    public static CondominioPlataformaDTO from(Condominio condominio, List<Usuario> administradores) {
        return new CondominioPlataformaDTO(
                condominio.getId(),
                condominio.getNome(),
                condominio.getCnpj(),
                condominio.isAtivo() ? "ATIVO" : "INATIVO",
                condominio.getConfiguracaoInicial(),
                condominio.getModelo(),
                condominio.getCreatedAt(),
                administradores.stream().map(Administrador::from).toList()
        );
    }
}
