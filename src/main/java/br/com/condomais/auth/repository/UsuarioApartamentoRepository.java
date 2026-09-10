package br.com.condomais.auth.repository;

import br.com.condomais.auth.model.UsuarioApartamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface UsuarioApartamentoRepository extends JpaRepository<UsuarioApartamento, UUID> {
    List<UsuarioApartamento> findByUsuarioId(UUID usuarioId);
    List<UsuarioApartamento> findByApartamentoId(UUID apartamentoId);
}
