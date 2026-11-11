package br.com.condomais.interativo.model;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.condominio.model.AreaComum;
import br.com.condomais.condominio.model.Condominio;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "reservas")
@Getter @Setter @NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "data_reserva", nullable = false)
    private LocalDate dataReserva;

    // Dia em que a reserva termina: o dia seguinte quando passa da meia-noite (22h às 02h)
    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(name = "horario_inicio", nullable = false)
    private LocalTime horarioInicio;

    @Column(name = "horario_fim", nullable = false)
    private LocalTime horarioFim;

    @Column(nullable = false, length = 50)
    private String status; // CONFIRMADA, AGUARDANDO APROVACAO, AGUARDANDO PAGAMENTO, CANCELADA, RECUSADA

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id", nullable = false)
    private AreaComum area;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "morador_id", nullable = false)
    private Usuario morador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprovador_id")
    private Usuario aprovador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;

    // Taxa da área no momento do pedido (nula = gratuita); a reserva paga só confirma depois do pagamento
    @Column(name = "valor_taxa", precision = 10, scale = 2)
    private BigDecimal valorTaxa;

    // Quem confirmou o pagamento manual (sempre o admin) e quando
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_confirmado_por_id")
    private Usuario pagamentoConfirmadoPor;

    @Column(name = "pagamento_confirmado_em")
    private LocalDateTime pagamentoConfirmadoEm;
}