package br.com.condomais.atendimento.repository;

import br.com.condomais.atendimento.model.Chamado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChamadoRepository extends JpaRepository<Chamado, UUID> {
    List<Chamado> findByCondominioId(UUID condominioId);

    Optional<Chamado> findByIdAndCondominioId(UUID id, UUID condominioId);

    boolean existsByCategoriaId(UUID categoriaId);

    // Administração e portaria: todos do condomínio, mais recentes primeiro
    List<Chamado> findByCondominioIdOrderByDataAberturaDesc(UUID condominioId);

    // Morador com unidade: os que ele abriu e os da unidade dele (abertos por outro morador ou pela portaria)
    @Query("SELECT c FROM Chamado c WHERE c.condominio.id = :condominioId " +
            "AND (c.solicitante.id = :usuarioId OR c.apartamento.id = :apartamentoId) ORDER BY c.dataAbertura DESC")
    List<Chamado> listarDoMorador(@Param("condominioId") UUID condominioId,
                                  @Param("usuarioId") UUID usuarioId,
                                  @Param("apartamentoId") UUID apartamentoId);

    // Morador sem unidade: só os que ele abriu
    List<Chamado> findByCondominioIdAndSolicitanteIdOrderByDataAberturaDesc(UUID condominioId, UUID solicitanteId);
}
