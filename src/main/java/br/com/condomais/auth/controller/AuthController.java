package br.com.condomais.auth.controller;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.auth.service.RecuperacaoSenhaService;
import br.com.condomais.auth.service.SenhaRegras;
import br.com.condomais.core.security.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Módulo 1 - Usuários, Autenticação e Permissões", description = "Gerenciamento de identificação, cadastro, autenticação (CPF e senha) e autorização de acesso.")
public class AuthController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RecuperacaoSenhaService recuperacaoSenhaService;

    public record LoginDTO(String cpf, String senha) {}
    public record TokenResponseDTO(String token) {}

    // Classe interna auxiliar (DTO) para não expor a Entidade
    public record PrimeiroAcessoDTO(String cpf, String novaSenha) {}

    public record RecuperacaoSenhaDTO(String cpf) {}
    public record RedefinicaoSenhaDTO(String token, String novaSenha) {}
    public record MensagemDTO(String mensagem) {}

    @PostMapping("/primeiro-acesso")
    @Operation(
            summary = "Primeiro acesso e criação de senha",
            description = "Permite que um usuário com CPF previamente cadastrado administrativamente, e que ainda não possui senha, crie sua própria senha. O sistema não possui cadastro público livre (RN-AUT-001)."
    )
    public ResponseEntity<String> primeiroAcesso(@RequestBody PrimeiroAcessoDTO dados) {
        // Mesmas regras do front e da recuperação (8 a 72 bytes)
        var problema = SenhaRegras.problema(dados.novaSenha());
        if (problema != null) {
            return ResponseEntity.badRequest().body(problema);
        }

        // Consulta se o CPF já está pré-cadastrado pela Administração
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCpf(dados.cpf());

        // Rota pública: só cria senha para quem ainda não tem, senão qualquer um com o CPF
        // tomaria a conta. Mesma resposta nos dois casos para não revelar quais CPFs existem.
        if (usuarioOpt.isEmpty() || usuarioOpt.get().getSenha() != null) {
            return ResponseEntity.status(403).body("Primeiro acesso indisponível para este CPF. Se você já possui senha, utilize a recuperação de senha.");
        }

        Usuario usuario = usuarioOpt.get();

        // Criptografa a senha criada pelo próprio usuário no primeiro acesso
        usuario.setSenha(passwordEncoder.encode(dados.novaSenha()));
        usuarioRepository.save(usuario);

        return ResponseEntity.ok("Senha criada com sucesso. Você já pode realizar o login.");
    }

    @PostMapping("/recuperar-senha")
    @Operation(
            summary = "Pedir recuperação de senha por e-mail",
            description = "Envia ao e-mail cadastrado do CPF um link de uso único, válido por 30 minutos, para criar uma nova senha. "
                    + "A resposta é sempre a mesma (não revela se o CPF existe ou tem e-mail); no máximo 3 pedidos por hora por usuário. "
                    + "503 se o envio de e-mail não estiver configurado (BREVO_API_KEY e EMAIL_REMETENTE)."
    )
    public ResponseEntity<MensagemDTO> recuperarSenha(@RequestBody RecuperacaoSenhaDTO dados) {
        recuperacaoSenhaService.solicitar(dados == null ? null : dados.cpf());
        return ResponseEntity.ok(new MensagemDTO(RecuperacaoSenhaService.RESPOSTA_PEDIDO));
    }

    @PostMapping("/redefinir-senha")
    @Operation(
            summary = "Criar nova senha pelo link do e-mail",
            description = "Recebe o token do link e a nova senha (8 a 72 caracteres). 400 se o link for inválido, já usado ou expirado; "
                    + "ao redefinir, os outros links abertos do mesmo usuário deixam de valer."
    )
    public ResponseEntity<MensagemDTO> redefinirSenha(@RequestBody RedefinicaoSenhaDTO dados) {
        recuperacaoSenhaService.redefinir(dados == null ? null : dados.token(), dados == null ? null : dados.novaSenha());
        return ResponseEntity.ok(new MensagemDTO("Senha alterada com sucesso. Você já pode entrar com a nova senha."));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Autenticação de usuário",
            description = "Realiza a validação de acesso utilizando CPF e senha (RF-AUT-001). Identifica o perfil do usuário e retorna o token de acesso (RF-AUT-003)."
    )
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