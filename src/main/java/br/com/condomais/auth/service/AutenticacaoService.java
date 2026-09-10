package br.com.condomais.auth.service;

import br.com.condomais.auth.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AutenticacaoService implements UserDetailsService {

    @Autowired
    private UsuarioRepository repository;

    @Override
    public UserDetails loadUserByUsername(String cpf) throws UsernameNotFoundException {
        // Regra RN-AUT-001: Autenticação via CPF previamente cadastrado
        var usuario = repository.findByCpf(cpf)
                .orElseThrow(() -> new UsernameNotFoundException("CPF não encontrado ou não autorizado."));

        // Aqui adaptamos a entidade Usuario para o formato que o Spring Security entende
        return org.springframework.security.core.userdetails.User.builder()
                .username(usuario.getCpf())
                .password(usuario.getSenha())
                .roles(usuario.getPerfil()) // Admin, Portaria, Morador
                .build();
    }
}