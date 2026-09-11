package br.com.condomais.core.security;

import br.com.condomais.auth.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var tokenJWT = recuperarToken(request);

        if (tokenJWT != null) {
            // Recupera o CPF do usuário a partir do token
            var subject = tokenService.getSubject(tokenJWT);
            var usuario = usuarioRepository.findByCpf(subject).orElseThrow();

            // Adapta para o formato do Spring Security garantindo os perfis e permissões do usuário
            var userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username(usuario.getCpf())
                    .password(usuario.getSenha())
                    .roles(usuario.getPerfil())
                    .build();

            var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            // Força a autenticação neste request
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String recuperarToken(HttpServletRequest request) {
        var authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null) {
            return authorizationHeader.replace("Bearer ", "");
        }
        return null;
    }
}
