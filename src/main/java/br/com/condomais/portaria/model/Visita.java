package br.com.condomais.portaria.model;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "visitas")
@Getter @Setter @NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Visita {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "data_prevista")
    private LocalDate dataPrevista;

    @Column(name = "horario_previsto")
    private LocalTime horarioPrevisto;

    // Fim do período em que a entrada está autorizada (início em dataPrevista/horarioPrevisto)
    @Column(name = "data_fim_prevista")
    private LocalDate dataFimPrevista;

    @Column(name = "horario_fim_previsto")
    private LocalTime horarioFimPrevisto;

    // Quando a visita foi cadastrada, e por quem: PORTARIA (portaria ou administração) ou MORADOR
    @Column(name = "data_hora_registro")
    private LocalDateTime dataHoraRegistro;

    @Column(length = 20)
    private String origem;

    @Column(name = "data_hora_entrada")
    private LocalDateTime dataHoraEntrada;

    @Column(name = "data_hora_saida")
    private LocalDateTime dataHoraSaida;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visitante_id", nullable = false)
    private Visitante visitante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id", nullable = false)
    private Apartamento apartamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "morador_id")
    private Usuario morador;

    // Rastreabilidade: qual porteiro autorizou a entrada e saída (RN-POR-010)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "porteiro_entrada_id")
    private Usuario porteiroEntrada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "porteiro_saida_id")
    private Usuario porteiroSaida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;
}