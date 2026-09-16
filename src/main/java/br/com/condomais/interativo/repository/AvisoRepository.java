package br.com.condomais.interativo.repository;

import br.com.condomais.interativo.model.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AvisoRepository extends JpaRepository<Aviso, UUID> {

    // Busca avisos do condomínio que sejam gerais (torre nula) ou da torre específica do morador, validando a expiração
    @Query("SELECT a FROM Aviso a WHERE a.condominio.id = :condominioId " +
            "AND (a.torre IS NULL OR a.torre.id = :torreId) " +
            "AND (a.dataValidade IS NULL OR a.dataValidade >= :hoje) " +
            "ORDER BY a.createdAt DESC")
    List<Aviso> buscarAvisosAtivosParaMorador(@Param("condominioId") UUID condominioId,
                                              @Param("torreId") UUID torreId,
                                              @Param("hoje") LocalDate hoje);
}