package br.com.condomais.portaria.model;

import br.com.condomais.condominio.model.Condominio;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * Pessoa que visita o condomínio. O cadastro é por condomínio (o mesmo CPF pode existir em dois), para que
 * a portaria de um não veja o nome e o telefone que outro registrou.
 */
@Entity
@Table(name = "visitantes")
@Getter @Setter @NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Visitante {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 255)
    private String nome;

    // Único por condomínio (índice visitantes_cpf_unico_no_condominio, criado por SQL)
    @Column(nullable = false, length = 14)
    private String cpf;

    @Column(length = 20)
    private String telefone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;
}
