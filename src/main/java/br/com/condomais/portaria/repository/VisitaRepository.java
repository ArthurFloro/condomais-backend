package br.com.condomais.portaria.repository;

import br.com.condomais.portaria.model.Visita;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface VisitaRepository extends JpaRepository<Visita, UUID> {
    List<Visita> findByCondominioId(UUID condominioId);
    List<Visita> findByCondominioIdAndStatus(UUID condominioId, String status);
}