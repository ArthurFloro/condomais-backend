package br.com.condomais.auth.controller;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.core.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService tokenService;

    public record LoginDTO(String cpf, String senha) {}
    public record TokenResponseDTO(String token) {}

    // Classe interna auxiliar (DTO) para não expor a Entidade
    public record PrimeiroAcessoDTO(String cpf, String novaSenha) {}

    @PostMapping("/primeiro-acesso")
    public ResponseEntity<String> primeiroAcesso(@RequestBody PrimeiroAcessoDTO dados) {
        // Consulta se o CPF já está pré-cadastrado pela Administração
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCpf(dados.cpf());

        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(403).body("Acesso negado: CPF não possui cadastro prévio no condomínio.");
        }

        Usuario usuario = usuarioOpt.get();

        // Criptografa a senha criada pelo próprio usuário no primeiro acesso
        usuario.setSenha(passwordEncoder.encode(dados.novaSenha()));
        usuarioRepository.save(usuario);

        return ResponseEntity.ok("Senha criada com sucesso. Você já pode realizar o login.");
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> login(@RequestBody LoginDTO dados) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(dados.cpf(), dados.senha);

        // O Spring Security chama o AutenticacaoService que criamos antes
        Authentication auth = authenticationManager.authenticate(usernamePassword);

        // Recuperamos o usuário validado no banco
        Usuario usuario = usuarioRepository.findByCpf(dados.cpf()).orElseThrow();

        // Geramos o token com os dados de isolamento do condomínio
        String token = tokenService.gerarToken(usuario);

        return ResponseEntity.ok(new TokenResponseDTO(token));
    }
}