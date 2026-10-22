package br.com.condomais.condominio.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "condominios")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Condominio {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 255)
    private String nome;

    @Column(nullable = false, unique = true, length = 18)
    private String cnpj;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(length = 255)
    private String endereco;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String estado; // UF

    // PENDENTE ao ser cadastrado pelo MASTER; CONCLUIDA depois do Setup Wizard (POST /condominios/configuracao-inicial).
    // O @ColumnDefault é para o ddl-auto=update local conseguir criar a coluna numa tabela com linhas
    // (as que já existem contam como configuradas, como na migração 2026-10-21-configuracao-inicial.sql)
    @Column(name = "configuracao_inicial", nullable = false, length = 20)
    @ColumnDefault("'CONCLUIDA'")
    private String configuracaoInicial = "PENDENTE";

    // Desativado pelo MASTER: nenhum usuário do condomínio consegue logar (os dados ficam)
    public boolean isAtivo() {
        return !"INATIVO".equalsIgnoreCase(status);
    }

    public boolean isConfiguracaoConcluida() {
        return "CONCLUIDA".equals(configuracaoInicial);
    }
}
