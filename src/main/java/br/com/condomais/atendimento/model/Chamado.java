package br.com.condomais.atendimento.model;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chamados")
@Getter @Setter @NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Chamado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String descricao;

    @Column(name = "imagem_url", length = 255)
    private String imagemUrl;

    @Column(nullable = false, length = 50)
    private String prioridade; // BAIXA, NORMAL, ALTA, URGENTE

    @Column(nullable = false, length = 50)
    private String status; // ABERTO, EM ANÁLISE, EM ATENDIMENTO, RESOLVIDO, ENCERRADO

    @Column(name = "sla_prazo")
    private LocalDateTime slaPrazo;

    @Column(name = "responsavel_externo", length = 255)
    private String responsavelExterno;

    @Column(name = "contato_externo", length = 255)
    private String contatoExterno;

    @Column(name = "data_previsao_atendimento")
    private LocalDate dataPrevisaoAtendimento;

    @Column(name = "data_abertura", nullable = false)
    private LocalDateTime dataAbertura;

    @Column(name = "data_encerramento")
    private LocalDateTime dataEncerramento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaChamado categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id")
    private Apartamento apartamento; // Opcional, pois pode ser uma área comum

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Usuario solicitante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;
}