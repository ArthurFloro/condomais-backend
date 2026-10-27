package br.com.condomais.condominio.repository;

import br.com.condomais.condominio.model.AreaComum;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AreaComumRepository extends JpaRepository<AreaComum, UUID> {
    List<AreaComum> findByCondominioId(UUID condominioId);
    Optional<AreaComum> findByIdAndCondominioId(UUID id, UUID condominioId);

    // Trava a área durante uma solicitação de reserva: duas pedidas simultâneas para o mesmo horário
    // passariam as duas pela checagem de conflito; com a trava, a segunda espera e vê a primeira
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AreaComum a where a.id = :id and a.condominio.id = :condominioId")
    Optional<AreaComum> findParaReserva(@Param("id") UUID id, @Param("condominioId") UUID condominioId);
    List<AreaComum> findByAtivoTrue();
    List<AreaComum> findByCondominioIdAndAtivoTrue(UUID condominioId);
    boolean existsByCondominioId(UUID condominioId);
    boolean existsByCondominioIdAndNomeIgnoreCase(UUID condominioId, String nome);
}
