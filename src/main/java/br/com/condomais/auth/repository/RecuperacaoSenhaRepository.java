package br.com.condomais.auth.repository;

import br.com.condomais.auth.model.RecuperacaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, UUID> {
    Optional<RecuperacaoSenha> findByTokenHash(String tokenHash);

    long countByUsuarioIdAndCriadoEmAfter(UUID usuarioId, LocalDateTime desde);

    // Ao redefinir, os outros links ainda abertos do mesmo usuário deixam de valer
    @Modifying
    @Query("update RecuperacaoSenha r set r.usadoEm = :agora where r.usuario.id = :usuarioId and r.usadoEm is null")
    int invalidarAbertas(@Param("usuarioId") UUID usuarioId, @Param("agora") LocalDateTime agora);
}
