package br.com.condomais.core.security;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.service.UsuarioService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UsuarioAutenticado implements UserDetails {

    private final Usuario usuario;

    public UsuarioAutenticado(Usuario usuario) {
        this.usuario = usuario;
    }

    public UUID getId() {
        return usuario.getId();
    }

    // Nulo para o MASTER, que não pertence a nenhum condomínio (o SecurityConfig o mantém fora das rotas de condomínio)
    public UUID getCondominioId() {
        return usuario.getCondominio() != null ? usuario.getCondominio().getId() : null;
    }

    public String getPerfil() {
        return usuario.getPerfil();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Normalizado para que perfis antigos ("Administrador", "Porteiro") caiam nas mesmas regras de ADMIN e PORTARIA
        return List.of(new SimpleGrantedAuthority("ROLE_" + UsuarioService.normalizarPerfil(usuario.getPerfil())));
    }

    @Override
    public String getPassword() {
        return usuario.getSenha();
    }

    @Override
    public String getUsername() {
        return usuario.getCpf();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() {
        // Usuário inativo ou condomínio desativado pelo MASTER: sem login e sem acesso com token já emitido
        return usuario.isAtivo() && (usuario.getCondominio() == null || usuario.getCondominio().isAtivo());
    }
}