package br.com.condomais.atendimento.repository;

import br.com.condomais.atendimento.model.CategoriaChamado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoriaChamadoRepository extends JpaRepository<CategoriaChamado, UUID> {
    Optional<CategoriaChamado> findByIdAndCondominioId(UUID id, UUID condominioId);
    List<CategoriaChamado> findByCondominioIdOrderByNomeAsc(UUID condominioId);
    Optional<CategoriaChamado> findFirstByCondominioIdAndNomeIgnoreCase(UUID condominioId, String nome);
    boolean existsByCondominioIdAndNomeIgnoreCase(UUID condominioId, String nome);
    boolean existsByCondominioIdAndNomeIgnoreCaseAndIdNot(UUID condominioId, String nome, UUID id);
}
