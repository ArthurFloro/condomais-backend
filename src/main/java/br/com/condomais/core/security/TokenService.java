package br.com.condomais.core.security;


import br.com.condomais.auth.model.Usuario;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TokenService {

    // Geração e validação precisam usar o mesmo issuer, senão todo token é rejeitado
    private static final String ISSUER = "condomais-api";

    @Value("${api.security.token.secret:condoplus-secret-key}")
    private String secret;

    public String gerarToken(Usuario usuario) {
        try {
            Algorithm algoritmo = Algorithm.HMAC256(secret);
            var token = JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(usuario.getCpf())
                    .withClaim("id", usuario.getId().toString())
                    .withClaim("perfil", usuario.getPerfil());
            // O MASTER não tem condomínio: o token dele sai sem a claim
            if (usuario.getCondominio() != null) {
                token.withClaim("condominio_id", usuario.getCondominio().getId().toString());
            }
            return token.withExpiresAt(gerarDataExpiracao()).sign(algoritmo);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar o token JWT", exception);
        }
    }

    public Instant gerarDataExpiracao() {
        // Instant é absoluto: 8 horas a partir de agora, qualquer que seja o fuso do servidor
        return Instant.now().plus(8, ChronoUnit.HOURS);
    }

    public String getSubject(String token){
        try {
            Algorithm algoritmo = Algorithm.HMAC256(secret);
            return JWT.require(algoritmo)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            throw new RuntimeException("Token JWT inválido ou expirado!");
        }
    }
}
