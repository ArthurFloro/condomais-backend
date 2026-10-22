package br.com.condomais.core.security;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.service.CondominioService;
import br.com.condomais.plataforma.service.PlataformaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regras do SecurityConfig de ponta a ponta (filtro + autorização + respostas 401/403), com os
 * usuários e os services simulados: o MASTER só entra em /plataforma/** (e /usuarios/me), os
 * demais perfis não entram lá, e condomínio desativado derruba login e token.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegrasDeAcessoTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private PlataformaService plataformaService;
    @MockitoBean private CondominioService condominioService;

    private Condominio condominio;

    @BeforeEach
    void setUp() {
        condominio = new Condominio();
        condominio.setId(UUID.randomUUID());
        condominio.setStatus("ATIVO");
        when(plataformaService.listar(any())).thenReturn(List.of());
        when(condominioService.listarTorres(any())).thenReturn(List.of());
    }

    // CPFs fictícios, só para identificar cada usuário simulado
    private Usuario usuario(String cpf, String perfil, Condominio condominio) {
        var usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Fulano");
        usuario.setCpf(cpf);
        usuario.setPerfil(perfil);
        usuario.setStatus("ATIVO");
        usuario.setSenha(passwordEncoder.encode("senha-de-teste"));
        usuario.setCondominio(condominio);
        when(usuarioRepository.findComCondominioByCpf(cpf)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByCpf(cpf)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + tokenService.gerarToken(usuario);
    }

    @Test
    void semTokenRespondeNaoAutenticado() throws Exception {
        mockMvc.perform(get("/condominios/torres")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/plataforma/condominios")).andExpect(status().isUnauthorized());
    }

    @Test
    void masterEntraNaPlataformaMasNaoNoCondominio() throws Exception {
        var master = usuario("000.000.000-01", "MASTER", null);

        mockMvc.perform(get("/plataforma/condominios").header("Authorization", bearer(master)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/condominios/torres").header("Authorization", bearer(master)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.caminho").value("/condominios/torres"));
    }

    @Test
    void masterConsultaOsProprioDados() throws Exception {
        var master = usuario("000.000.000-02", "MASTER", null);

        mockMvc.perform(get("/usuarios/me").header("Authorization", bearer(master)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("MASTER"));
    }

    @Test
    void perfisDoCondominioNaoEntramNaPlataforma() throws Exception {
        for (var perfil : List.of("ADMIN", "PORTARIA", "MORADOR")) {
            var usuario = usuario("000.000.000-1" + perfil.length(), perfil, condominio);
            mockMvc.perform(get("/plataforma/condominios").header("Authorization", bearer(usuario)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensagem").value("Seu perfil não tem acesso a esta área."));
        }
    }

    @Test
    void perfisDoCondominioContinuamComAcesso() throws Exception {
        // "Administrador" é um valor antigo do banco: continua tratado como ADMIN
        for (var perfil : List.of("ADMIN", "Administrador", "PORTARIA", "MORADOR")) {
            var usuario = usuario("000.000.000-2" + perfil.length(), perfil, condominio);
            mockMvc.perform(get("/condominios/torres").header("Authorization", bearer(usuario)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void condominioDesativadoDerrubaTokenJaEmitido() throws Exception {
        var admin = usuario("000.000.000-03", "ADMIN", condominio);
        var token = bearer(admin);

        condominio.setStatus("INATIVO");

        mockMvc.perform(get("/condominios/torres").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void condominioDesativadoBloqueiaLogin() throws Exception {
        usuario("000.000.000-04", "ADMIN", condominio);
        var login = """
                {"cpf":"000.000.000-04","senha":"senha-de-teste"}""";

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk());

        condominio.setStatus("INATIVO");

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());
    }
}
