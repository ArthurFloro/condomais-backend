package br.com.condomais.portaria.model;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "encomendas")
@Getter @Setter @NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Encomenda {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "codigo_identificacao", nullable = false, length = 255)
    private String codigoIdentificacao;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(nullable = false, length = 50)
    private String status; // RECEBIDA, AGUARDANDO RETIRADA, RETIRADA, DEVOLVIDA

    @Column(name = "data_hora_recebimento", nullable = false)
    private LocalDateTime dataHoraRecebimento;

    @Column(name = "data_hora_retirada")
    private LocalDateTime dataHoraRetirada;

    // Nula quando a encomenda chega sem destinatário identificado (CONDO-05.1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartamento_id")
    private Apartamento apartamento;

    // Quem retirou: nome em toda retirada; na encomenda sem unidade, também o apartamento que a pessoa informou
    // (só registrado, para rastreio: a encomenda não passa a ser daquela unidade)
    @Column(name = "retirante_nome", length = 255)
    private String retiranteNome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retirante_apartamento_id")
    private Apartamento retiranteApartamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "morador_id")
    private Usuario morador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "porteiro_recebimento_id", nullable = false)
    private Usuario porteiroRecebimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "porteiro_retirada_id")
    private Usuario porteiroRetirada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;
}