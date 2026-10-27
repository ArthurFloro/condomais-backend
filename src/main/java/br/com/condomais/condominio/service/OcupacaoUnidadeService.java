package br.com.condomais.condominio.service;

import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Mantém o status da unidade de acordo com os moradores: com morador ativo vinculado ela fica OCUPADO;
 * quando o último sai (troca de unidade, é desativado ou vira admin), volta a LIVRE. INATIVO e RESERVADO
 * são definidos à mão pelo admin e nunca são sobrescritos aqui.
 */
@Service
public class OcupacaoUnidadeService {

    private static final Set<String> MANUAIS = Set.of("INATIVO", "RESERVADO");

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ApartamentoRepository apartamentoRepository;

    /** Recalcula as unidades informadas (nulas são ignoradas; a mesma unidade duas vezes conta uma). */
    @Transactional
    public void recalcular(Apartamento... unidades) {
        Stream.of(unidades).filter(Objects::nonNull).distinct().forEach(this::recalcularUma);
    }

    private void recalcularUma(Apartamento unidade) {
        var status = unidade.getStatus() == null ? "" : unidade.getStatus().trim().toUpperCase();
        if (MANUAIS.contains(status)) return;

        // A consulta força o flush do que mudou nesta transação, então o morador recém-salvo já conta
        var ocupada = usuarioRepository.existeMoradorAtivoNaUnidade(unidade.getId());
        if (ocupada && !"OCUPADO".equals(status)) {
            unidade.setStatus("OCUPADO");
            apartamentoRepository.save(unidade);
        } else if (!ocupada && "OCUPADO".equals(status)) {
            unidade.setStatus("LIVRE");
            apartamentoRepository.save(unidade);
        }
    }
}
