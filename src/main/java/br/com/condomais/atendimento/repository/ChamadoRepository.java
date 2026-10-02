package br.com.condomais.atendimento.repository;

import br.com.condomais.atendimento.model.Chamado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChamadoRepository extends JpaRepository<Chamado, UUID> {
    List<Chamado> findByCondominioId(UUID condominioId);

    // Assistente: chamados abertos pelo morador, mais recentes primeiro
    List<Chamado> findTop20ByCondominioIdAndSolicitanteIdOrderByDataAberturaDesc(UUID condominioId, UUID solicitanteId);
}