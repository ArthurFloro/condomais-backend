package br.com.condomais.condominio.repository;

import br.com.condomais.condominio.model.Condominio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CondominioRepository extends JpaRepository<Condominio, UUID> {
    Optional<Condominio> findByCnpj(String cnpj);
    boolean existsByCnpjAndIdNot(String cnpj, UUID id);

    // Trava a linha durante a configuração inicial: um segundo envio simultâneo (duplo clique) espera o
    // primeiro terminar e então vê CONCLUIDA, em vez de gerar a estrutura duas vezes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Condominio c where c.id = :id")
    Optional<Condominio> findParaConfiguracao(@Param("id") UUID id);
}
