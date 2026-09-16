package br.com.condomais.condominio.repository;

import br.com.condomais.condominio.model.AreaComum;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AreaComumRepository extends JpaRepository<AreaComum, UUID> {
    List<AreaComum> findByCondominioId(UUID condominioId);
    Optional<AreaComum> findByIdAndCondominioId(UUID id, UUID condominioId);
}