package br.com.condomais.interativo.repository;

import br.com.condomais.interativo.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    // Verifica sobreposição de períodos com data e hora completas (RF-RES-004), inclusive o que passa da
    // meia-noite: existente começa antes do fim do novo E termina depois do início dele.
    // Cancelada e recusada liberam o horário; confirmada e aguardando aprovação ocupam.
    // data_fim nula (reserva antiga, antes da coluna existir) vale o próprio dia da reserva.
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.area.id = :areaId " +
            "AND r.status NOT IN ('CANCELADA', 'RECUSADA') " +
            "AND (r.dataReserva < :dataFim OR (r.dataReserva = :dataFim AND r.horarioInicio < :horaFim)) " +
            "AND (COALESCE(r.dataFim, r.dataReserva) > :dataInicio " +
            "     OR (COALESCE(r.dataFim, r.dataReserva) = :dataInicio AND r.horarioFim > :horaInicio))")
    boolean existeConflitoDeHorario(@Param("areaId") UUID areaId,
                                    @Param("dataInicio") LocalDate dataInicio,
                                    @Param("horaInicio") LocalTime horaInicio,
                                    @Param("dataFim") LocalDate dataFim,
                                    @Param("horaFim") LocalTime horaFim);

    Optional<Reserva> findByIdAndCondominioId(UUID id, UUID condominioId);

    // Administração e portaria: todas do condomínio, da data mais recente para a mais antiga
    @Query("SELECT r FROM Reserva r WHERE r.condominio.id = :condominioId ORDER BY r.dataReserva DESC, r.horarioInicio DESC")
    List<Reserva> listarDoCondominio(@Param("condominioId") UUID condominioId);

    // Morador: as reservas feitas por quem mora na mesma unidade dele
    @Query("SELECT r FROM Reserva r WHERE r.condominio.id = :condominioId AND r.morador.apartamento.id = :apartamentoId " +
            "ORDER BY r.dataReserva DESC, r.horarioInicio DESC")
    List<Reserva> listarDaUnidade(@Param("condominioId") UUID condominioId, @Param("apartamentoId") UUID apartamentoId);

    // Agenda de uma área num dia: só os horários que ocupam, sem dados de quem reservou. Entra também a
    // reserva que começou na véspera e termina neste dia.
    @Query("SELECT r FROM Reserva r WHERE r.condominio.id = :condominioId AND r.area.id = :areaId " +
            "AND r.dataReserva <= :data AND COALESCE(r.dataFim, r.dataReserva) >= :data " +
            "AND r.status NOT IN ('CANCELADA', 'RECUSADA') ORDER BY r.dataReserva, r.horarioInicio")
    List<Reserva> listarOcupacao(@Param("condominioId") UUID condominioId, @Param("areaId") UUID areaId, @Param("data") LocalDate data);
}
