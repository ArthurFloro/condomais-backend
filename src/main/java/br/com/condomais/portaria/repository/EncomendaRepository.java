package br.com.condomais.portaria.repository;

import br.com.condomais.portaria.model.Encomenda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EncomendaRepository extends JpaRepository<Encomenda, UUID> {
    List<Encomenda> findByCondominioId(UUID condominioId);
    List<Encomenda> findByCondominioIdAndApartamentoId(UUID condominioId, UUID apartamentoId);

    Optional<Encomenda> findByIdAndCondominioId(UUID id, UUID condominioId);

    // Listagens, das mais recentes para as mais antigas
    List<Encomenda> findByCondominioIdOrderByDataHoraRecebimentoDesc(UUID condominioId);
    List<Encomenda> findByCondominioIdAndStatusOrderByDataHoraRecebimentoDesc(UUID condominioId, String status);
    List<Encomenda> findByCondominioIdAndApartamentoIdOrderByDataHoraRecebimentoDesc(UUID condominioId, UUID apartamentoId);
    List<Encomenda> findByCondominioIdAndApartamentoIdAndStatusOrderByDataHoraRecebimentoDesc(UUID condominioId, UUID apartamentoId, String status);
}
