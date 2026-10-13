package br.com.condomais.interativo.repository;

import br.com.condomais.interativo.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    // Verifica sobreposição de horários (bloqueio de reservas conflitantes - RF-RES-004)
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.area.id = :areaId " +
            "AND r.dataReserva = :data " +
            "AND r.status != 'CANCELADA' " +
            "AND (r.horarioInicio < :fim AND r.horarioFim > :inicio)")
    boolean existeConflitoDeHorario(@Param("areaId") UUID areaId,
                                    @Param("data") LocalDate data,
                                    @Param("inicio") LocalTime inicio,
                                    @Param("fim") LocalTime fim);
}