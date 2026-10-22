package br.com.condomais.core.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private SecurityFilter securityFilter;

    // Origens do front-end autorizadas a chamar a API (separadas por vírgula)
    @Value("${api.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(req -> {
                    // Rotas públicas para login e primeiro acesso
                    req.requestMatchers("/auth/login", "/auth/primeiro-acesso").permitAll();
                    // Documentação da API (Swagger UI / OpenAPI); as rotas em si continuam protegidas
                    req.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    // Página de erro do Spring: sem isso, qualquer erro não tratado virava 403
                    req.requestMatchers("/error").permitAll();
                    // Área da plataforma: só o MASTER (donos do Condo+), que cadastra condomínios e os admins deles
                    req.requestMatchers("/plataforma/**").hasRole("MASTER");
                    // Dados do próprio usuário: qualquer perfil, inclusive o MASTER (o front decide a área por aqui)
                    req.requestMatchers("/usuarios/me").authenticated();
                    // Todo o resto é de dentro de um condomínio: exige login e fica fechado para o MASTER,
                    // que não tem condomínio e não acessa os dados internos de nenhum
                    req.anyRequest().access(AuthorizationManagers.allOf(
                            AuthenticatedAuthorizationManager.authenticated(),
                            AuthorizationManagers.not(AuthorityAuthorizationManager.hasRole("MASTER"))));
                })
                // Sem token (ou token inválido/expirado) em rota protegida: 401; perfil sem permissão: 403.
                // Os dois no mesmo formato do GlobalExceptionHandler
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::responderNaoAutenticado)
                        .accessDeniedHandler(this::responderAcessoNegado))
                // Adiciona o nosso filtro antes do UsernamePasswordAuthenticationFilter
                .addFilterBefore(securityFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void responderNaoAutenticado(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        responderErro(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Token ausente, inválido ou expirado.");
    }

    private void responderAcessoNegado(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException {
        responderErro(request, response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "Seu perfil não tem acesso a esta área.");
    }

    private void responderErro(HttpServletRequest request, HttpServletResponse response, int status, String erro, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"status":%d,"erro":"%s","mensagem":"%s","caminho":"%s","timestamp":"%s"}"""
                .formatted(status, erro, mensagem, request.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\""), Instant.now()));
    }

    // CORS para o front-end: token vai no header Authorization, então não há cookies/credentials
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // Criptografia e armazenamento técnico de senhas (BCrypt)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
