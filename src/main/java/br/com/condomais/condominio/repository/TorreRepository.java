package br.com.condomais.condominio.repository;

import br.com.condomais.condominio.model.Torre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TorreRepository extends JpaRepository<Torre, UUID> {
    List<Torre> findByCondominioId(UUID condominioId);
    Optional<Torre> findByIdAndCondominioId(UUID id, UUID condominioId);
    boolean existsByCondominioId(UUID condominioId);
    boolean existsByCondominioIdAndNomeIgnoreCase(UUID condominioId, String nome);
    boolean existsByCondominioIdAndNomeIgnoreCaseAndIdNot(UUID condominioId, String nome, UUID id);

    /** O que impede excluir a torre junto com as unidades: tudo que aponta para ela ou para uma unidade dela. */
    interface UsoDaTorre {
        long getMoradores();
        long getEncomendas();
        long getVisitas();
        long getChamados();
        long getAvisos();
    }

    // Usuários incluem inativos que ainda guardam a unidade e o vínculo legado (usuarios_apartamentos);
    // encomendas contam também a unidade informada na retirada sem destinatário.
    @Query(value = """
            select (select count(*) from usuarios u join apartamentos a on a.id = u.apartamento_id where a.torre_id = :torreId)
                 + (select count(*) from usuarios_apartamentos ua join apartamentos a on a.id = ua.apartamento_id where a.torre_id = :torreId) as moradores,
                   (select count(distinct e.id) from encomendas e join apartamentos a on a.id = e.apartamento_id or a.id = e.retirante_apartamento_id
                     where a.torre_id = :torreId) as encomendas,
                   (select count(*) from visitas v join apartamentos a on a.id = v.apartamento_id where a.torre_id = :torreId) as visitas,
                   (select count(*) from chamados c join apartamentos a on a.id = c.apartamento_id where a.torre_id = :torreId) as chamados,
                   (select count(*) from avisos av where av.torre_id = :torreId) as avisos
            """, nativeQuery = true)
    UsoDaTorre contarUso(@Param("torreId") UUID torreId);
}
