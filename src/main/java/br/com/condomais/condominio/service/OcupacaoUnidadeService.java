package br.com.condomais.condominio.service;

import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.core.exception.ConflitoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Mantém o status da unidade de acordo com os moradores: com morador ativo vinculado ela fica OCUPADO;
 * quando o último sai (troca de unidade, é desativado ou vira admin), volta a LIVRE. INATIVO e RESERVADO
 * são definidos à mão pelo admin, mas só valem para unidade sem morador: quem é vinculado a uma unidade
 * reservada ou inativa a deixa OCUPADO, e o admin não marca outro status enquanto houver morador.
 */
@Service
public class OcupacaoUnidadeService {

    static final String LIVRE = "LIVRE";
    static final String OCUPADO = "OCUPADO";
    private static final Set<String> STATUS = Set.of(LIVRE, OCUPADO, "INATIVO", "RESERVADO");

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ApartamentoRepository apartamentoRepository;

    /** Recalcula as unidades informadas (nulas são ignoradas; a mesma unidade duas vezes conta uma). */
    @Transactional
    public void recalcular(Apartamento... unidades) {
        Stream.of(unidades).filter(Objects::nonNull).distinct().forEach(this::recalcularUma);
    }

    /**
     * Status escolhido pelo admin no cadastro (unidade sem id) ou na edição de uma unidade. Ausente vale LIVRE
     * no cadastro e mantém o atual na edição. OCUPADO e LIVRE não contradizem os moradores: com morador ativo
     * a unidade só pode ficar OCUPADO, e sem morador não pode ficar OCUPADO.
     */
    @Transactional(readOnly = true)
    public String statusEscolhido(Apartamento unidade, String informado) {
        var nova = unidade.getId() == null;
        if (informado == null || informado.isBlank()) {
            return nova ? LIVRE : unidade.getStatus();
        }
        var status = informado.trim().toUpperCase();
        if (!STATUS.contains(status)) {
            throw new IllegalArgumentException("Status inválido. Use LIVRE, INATIVO ou RESERVADO (OCUPADO é definido pelos moradores).");
        }
        var ocupada = !nova && usuarioRepository.existeMoradorAtivoNaUnidade(unidade.getId());
        if (ocupada && !OCUPADO.equals(status)) {
            throw new ConflitoException("A unidade tem morador ativo e fica OCUPADO enquanto ele estiver vinculado. "
                    + "Desvincule ou desative os moradores antes de mudar o status.");
        }
        if (!ocupada && OCUPADO.equals(status)) {
            throw new ConflitoException("A unidade não tem morador ativo. Ela fica OCUPADO sozinha quando um morador é vinculado.");
        }
        return status;
    }

    private void recalcularUma(Apartamento unidade) {
        var status = unidade.getStatus() == null ? "" : unidade.getStatus().trim().toUpperCase();

        // A consulta força o flush do que mudou nesta transação, então o morador recém-salvo já conta
        var ocupada = usuarioRepository.existeMoradorAtivoNaUnidade(unidade.getId());
        if (ocupada && !OCUPADO.equals(status)) {
            unidade.setStatus(OCUPADO);
            apartamentoRepository.save(unidade);
        } else if (!ocupada && OCUPADO.equals(status)) {
            unidade.setStatus(LIVRE);
            apartamentoRepository.save(unidade);
        }
    }
}
