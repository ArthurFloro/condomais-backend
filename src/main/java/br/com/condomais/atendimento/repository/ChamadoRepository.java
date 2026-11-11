package br.com.condomais.atendimento.repository;

import br.com.condomais.atendimento.model.Chamado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChamadoRepository extends JpaRepository<Chamado, UUID> {
    List<Chamado> findByCondominioId(UUID condominioId);

    Optional<Chamado> findByIdAndCondominioId(UUID id, UUID condominioId);

    boolean existsByCategoriaId(UUID categoriaId);

    // Administração: todos do condomínio, mais recentes primeiro
    List<Chamado> findByCondominioIdOrderByDataAberturaDesc(UUID condominioId);

    // Morador: só os chamados que ele abriu (ocorrências privadas)
    List<Chamado> findByCondominioIdAndSolicitanteIdOrderByDataAberturaDesc(UUID condominioId, UUID solicitanteId);
}
