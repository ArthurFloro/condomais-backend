package br.com.condomais.condominio.repository;

import br.com.condomais.condominio.model.Torre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TorreRepository extends JpaRepository<Torre, UUID> {
    Optional<Torre> findByCondominioId(UUID condominioId);
}
