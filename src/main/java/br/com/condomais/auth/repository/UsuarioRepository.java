package br.com.condomais.auth.repository;

import br.com.condomais.auth.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    // Busca global por CPF (usada no login inicial antes de saber o condomínio)
    Optional<Usuario> findByCpf(String cpf);

    // Login e SecurityFilter: o condomínio vem junto porque o acesso depende de ele estar ativo
    @Query("select u from Usuario u left join fetch u.condominio where u.cpf = :cpf")
    Optional<Usuario> findComCondominioByCpf(@Param("cpf") String cpf);

    // Busca isolada por condomínio
    List<Usuario> findByCondominioId(UUID condominioId);

    List<Usuario> findByCondominioIdOrderByNomeAsc(UUID condominioId);

    Optional<Usuario> findByIdAndCondominioId(UUID id, UUID condominioId);

    List<Usuario> findByCondominioIdIn(Collection<UUID> condominioIds);
}
