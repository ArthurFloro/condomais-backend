package br.com.condomais.portaria.repository;

import br.com.condomais.portaria.model.Visitante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VisitanteRepository extends JpaRepository<Visitante, UUID> {
    Optional<Visitante> findByCpf(String cpf);
}
