package br.com.condomais.auth.model;

import br.com.condomais.condominio.model.Apartamento;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "usuarios_apartamentos")
@Getter @Setter @NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UsuarioApartamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "tipo_vinculo", nullable = false, length = 50)
    private String tipoVinculo; // Ex: Proprietário, Residente

    @Column(name = "status_aprovacao", nullable = false, length = 50)
    private String statusAprovacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id", nullable = false)
    private Apartamento apartamento;
}