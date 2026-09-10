package br.com.condomais.condominio.repository;

import br.com.condomais.condominio.model.Apartamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApartamentoRepository extends JpaRepository<Apartamento, UUID> {
    List<Apartamento> findByCondominioId(UUID condominioId);
    Optional<Apartamento> findByIdAndCondominioId(UUID id, UUID condominioId);
    List<Apartamento> findByTorreIdAndCondominioId(UUID torreId, UUID condominioId);
}
