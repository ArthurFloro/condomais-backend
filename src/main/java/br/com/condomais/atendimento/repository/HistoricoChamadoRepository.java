package br.com.condomais.atendimento.repository;

import br.com.condomais.atendimento.model.HistoricoChamado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HistoricoChamadoRepository extends JpaRepository<HistoricoChamado, UUID> {}
