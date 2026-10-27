package br.com.condomais.portaria.repository;

import br.com.condomais.portaria.model.Visita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitaRepository extends JpaRepository<Visita, UUID> {

    Optional<Visita> findByIdAndCondominioId(UUID id, UUID condominioId);

    // Portaria e administração: todas do condomínio, das cadastradas mais recentemente para as mais antigas
    @Query("SELECT v FROM Visita v JOIN FETCH v.visitante JOIN FETCH v.apartamento a LEFT JOIN FETCH a.torre " +
            "WHERE v.condominio.id = :condominioId ORDER BY v.dataHoraRegistro DESC NULLS LAST")
    List<Visita> listarDoCondominio(@Param("condominioId") UUID condominioId);

    // Morador: as visitas da própria unidade
    @Query("SELECT v FROM Visita v JOIN FETCH v.visitante JOIN FETCH v.apartamento a LEFT JOIN FETCH a.torre " +
            "WHERE v.condominio.id = :condominioId AND a.id = :apartamentoId ORDER BY v.dataHoraRegistro DESC NULLS LAST")
    List<Visita> listarDaUnidade(@Param("condominioId") UUID condominioId, @Param("apartamentoId") UUID apartamentoId);
}
