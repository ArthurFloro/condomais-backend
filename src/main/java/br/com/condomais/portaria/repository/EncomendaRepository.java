package br.com.condomais.portaria.repository;

import br.com.condomais.portaria.model.Encomenda;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EncomendaRepository extends JpaRepository<Encomenda, UUID> {
    List<Encomenda> findByCondominioId(UUID condominioId);
    List<Encomenda> findByCondominioIdAndApartamentoId(UUID condominioId, UUID apartamentoId);
}