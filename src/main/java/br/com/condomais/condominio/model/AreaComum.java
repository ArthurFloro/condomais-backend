package br.com.condomais.condominio.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "areas_comuns")
@Getter @Setter @NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AreaComum {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 255)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(columnDefinition = "TEXT")
    private String regras;

    @Column(nullable = false)
    private Boolean reservavel;

    @Column(name = "exige_aprovacao", nullable = false)
    private Boolean exigeAprovacao;

    @Column(nullable = false)
    private Boolean ativo = true;

    // Capacidade máxima de pessoas; opcional (§6.4 da especificação)
    private Integer capacidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;
}