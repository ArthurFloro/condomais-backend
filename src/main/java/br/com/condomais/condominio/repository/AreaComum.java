package br.com.condomais.condominio.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface AreaComum extends JpaRepository<AreaComum, UUID> {
    List<AreaComum> findByCondominioId(UUID condominioId);
    Optional<AreaComum> findByCondominioId(UUID id, UUID condominioId);
}
