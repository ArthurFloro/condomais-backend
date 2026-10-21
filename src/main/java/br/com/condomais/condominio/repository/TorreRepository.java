package br.com.condomais.condominio.repository;

import br.com.condomais.condominio.model.Torre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TorreRepository extends JpaRepository<Torre, UUID> {
    List<Torre> findByCondominioId(UUID condominioId);
    Optional<Torre> findByIdAndCondominioId(UUID id, UUID condominioId);
    boolean existsByCondominioId(UUID condominioId);
}
