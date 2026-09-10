package br.com.condomais.auth.repository;

import br.com.condomais.auth.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    // Busca global por CPF (usada no login inicial antes de saber o condomínio)
    Optional<Usuario> findByCpf(String cpf);

    // Busca isolada por condomínio
    List<Usuario> findByCondominioId(UUID condominioId);
}
