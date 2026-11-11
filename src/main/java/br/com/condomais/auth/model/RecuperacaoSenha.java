package br.com.condomais.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pedido de recuperação de senha por e-mail. Guarda só o hash (SHA-256) do token enviado no link: quem lê o
 * banco não consegue montar o link. Vale por pouco tempo e uma única vez.
 */
@Entity
@Table(name = "recuperacoes_senha")
@Getter
@Setter
public class RecuperacaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    // Preenchido quando a senha é redefinida (ou quando outro pedido do mesmo usuário é usado)
    @Column(name = "usado_em")
    private LocalDateTime usadoEm;
}
