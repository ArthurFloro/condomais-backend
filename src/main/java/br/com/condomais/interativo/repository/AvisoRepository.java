package br.com.condomais.interativo.repository;

import br.com.condomais.interativo.model.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvisoRepository extends JpaRepository<Aviso, UUID> {

    Optional<Aviso> findByIdAndCondominioId(UUID id, UUID condominioId);

    // Administração e portaria: todos os avisos do condomínio, inclusive os vencidos
    List<Aviso> findByCondominioIdOrderByCreatedAtDesc(UUID condominioId);

    // Morador de uma torre: avisos gerais (torre nula) e os da torre dele, ainda dentro da validade
    @Query("SELECT a FROM Aviso a WHERE a.condominio.id = :condominioId " +
            "AND (a.torre IS NULL OR a.torre.id = :torreId) " +
            "AND (a.dataValidade IS NULL OR a.dataValidade >= :hoje) " +
            "ORDER BY a.createdAt DESC")
    List<Aviso> buscarAvisosAtivosParaMorador(@Param("condominioId") UUID condominioId,
                                              @Param("torreId") UUID torreId,
                                              @Param("hoje") LocalDate hoje);

    // Morador sem torre (unidade direto no condomínio): só os avisos gerais
    @Query("SELECT a FROM Aviso a WHERE a.condominio.id = :condominioId AND a.torre IS NULL " +
            "AND (a.dataValidade IS NULL OR a.dataValidade >= :hoje) ORDER BY a.createdAt DESC")
    List<Aviso> buscarAvisosGeraisAtivos(@Param("condominioId") UUID condominioId, @Param("hoje") LocalDate hoje);
}
