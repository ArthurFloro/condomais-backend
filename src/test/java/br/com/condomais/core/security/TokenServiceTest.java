package br.com.condomais.core.security;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.condominio.model.Condominio;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {

    private static final String SECRET = "segredo-de-teste";

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
    }

    @Test
    void tokenGeradoEhAceitoNaValidacao() {
        var condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setCpf("12345678900");
        usuario.setPerfil("ADMINISTRADOR");
        usuario.setCondominio(condominio);

        var token = tokenService.gerarToken(usuario);

        assertEquals("12345678900", tokenService.getSubject(token));
    }

    @Test
    void tokenDoAdminTrazOCondominio() {
        var condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setCpf("12345678900");
        usuario.setPerfil("ADMIN");
        usuario.setCondominio(condominio);

        var claims = JWT.decode(tokenService.gerarToken(usuario)).getClaims();

        assertEquals(condominio.getId().toString(), claims.get("condominio_id").asString());
    }

    @Test
    void tokenDoMasterSaiSemCondominio() {
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setCpf("12345678900");
        usuario.setPerfil("MASTER");

        var token = tokenService.gerarToken(usuario);

        assertEquals("12345678900", tokenService.getSubject(token));
        assertEquals("MASTER", JWT.decode(token).getClaim("perfil").asString());
        assertFalse(JWT.decode(token).getClaims().containsKey("condominio_id"));
    }

    @Test
    void tokenDeOutroIssuerEhRejeitado() {
        var token = JWT.create()
                .withIssuer("outra-api")
                .withSubject("12345678900")
                .sign(Algorithm.HMAC256(SECRET));

        assertThrows(RuntimeException.class, () -> tokenService.getSubject(token));
    }

    @Test
    void tokenMalformadoEhRejeitado() {
        assertThrows(RuntimeException.class, () -> tokenService.getSubject("nao-eh-um-jwt"));
    }
}
