package br.com.condomais.condominio.repository;

import br.com.condomais.condominio.model.Condominio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CondominioRepository extends JpaRepository<Condominio, UUID> {
    Optional<Condominio> findByCnpj(String cnpj);
}
