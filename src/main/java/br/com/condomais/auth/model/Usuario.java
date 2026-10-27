package br.com.condomais.auth.model;

import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter @Setter @NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 255)
    private String nome;

    // Fora do JSON: rotas que ainda devolvem entidades (visitas, avisos, reservas, chamados) levam o Usuario
    // junto; a API de usuários usa UsuarioResponseDTO e não depende da entidade serializada
    @JsonIgnore
    @Column(nullable = false, unique = true, length = 14)
    private String cpf;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String telefone;

    // Nula até o primeiro acesso: a Administração pré-cadastra o usuário sem senha
    @JsonIgnore
    @Column(length = 255)
    private String senha;

    @Column(nullable = false, length = 50)
    private String perfil; // MASTER, ADMIN, PORTARIA, MORADOR

    // ATIVO ou INATIVO; nulo (registros antigos) conta como ativo. Inativo não consegue logar.
    @Column(length = 20)
    private String status = "ATIVO";

    // Unidade do morador; nulo para administração e portaria
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id")
    private Apartamento apartamento;

    @Column(length = 20)
    private String vinculo; // PROPRIETARIO, INQUILINO (só para MORADOR)

    // Nulo só para o MASTER (dono da plataforma); o banco garante isso com um CHECK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condominio_id")
    private Condominio condominio;

    public boolean isAtivo() {
        return !"INATIVO".equals(status);
    }
}
