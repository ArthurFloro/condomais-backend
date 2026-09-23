package br.com.condomais.condominio.service;

import br.com.condomais.condominio.dto.*;
import br.com.condomais.condominio.model.*;
import br.com.condomais.condominio.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CondominioService {

    @Autowired private CondominioRepository condominioRepository;
    @Autowired private TorreRepository torreRepository;
    @Autowired private ApartamentoRepository apartamentoRepository;
    @Autowired private AreaComumRepository areaComumRepository;

    @Transactional
    public Condominio cadastrarCondominio(CondominioDTO dados) {
        // Regra RN-CON-002: Somente a Administradora XYZ cadastra novos condomínios
        var condominio = new Condominio();
        condominio.setNome(dados.nome());
        condominio.setCnpj(dados.cnpj());
        condominio.setStatus(dados.status().toUpperCase());
        condominio.setCreatedAt(LocalDateTime.now());
        return condominioRepository.save(condominio);
    }

    @Transactional
    public Torre cadastrarTorre(TorreDTO dados) {
        var condominio = condominioRepository.findById(dados.condominioId())
                .orElseThrow(() -> new IllegalArgumentException("Condomínio não encontrado."));

        var torre = new Torre();
        torre.setNome(dados.nome());
        torre.setCondominio(condominio);
        return torreRepository.save(torre);
    }

    @Transactional
    public Apartamento cadastrarApartamento(ApartamentoDTO dados) {
        var condominio = condominioRepository.findById(dados.condominioId())
                .orElseThrow(() -> new IllegalArgumentException("Condomínio não encontrado."));

        var apartamento = new Apartamento();
        apartamento.setNumero(dados.numero());
        apartamento.setStatus(dados.status().toUpperCase());
        apartamento.setCondominio(condominio);

        if (dados.torreId() != null) {
            var torre = torreRepository.findById(dados.torreId())
                    .orElseThrow(() -> new IllegalArgumentException("Torre não encontrada."));
            apartamento.setTorre(torre);
        }

        return apartamentoRepository.save(apartamento);
    }

    @Transactional
    public AreaComum cadastrarAreaComum(AreaComumDTO dados) {
        var condominio = condominioRepository.findById(dados.condominioId())
                .orElseThrow(() -> new IllegalArgumentException("Condomínio não encontrado."));

        var area = new AreaComum();
        area.setNome(dados.nome());
        area.setDescricao(dados.descricao());
        area.setRegras(dados.regras());
        area.setReservavel(dados.reservavel());
        area.setExigeAprovacao(dados.exigeAprovacao());
        area.setCondominio(condominio);

        return areaComumRepository.save(area);
    }

    // ---------------------------------------------------------------------
    // Condomínio
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Condominio> listarCondominios() {
        return condominioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Condominio buscarCondominio(UUID id) {
        return condominioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Condomínio não encontrado."));
    }

    @Transactional
    public Condominio atualizarCondominio(UUID id, CondominioDTO dados) {
        var condominio = buscarCondominio(id);

        if (condominioRepository.existsByCnpjAndIdNot(dados.cnpj(), id)) {
            throw new IllegalArgumentException("Já existe outro condomínio cadastrado com este CNPJ.");
        }

        condominio.setNome(dados.nome());
        condominio.setCnpj(dados.cnpj());
        condominio.setStatus(dados.status().toUpperCase());
        return condominioRepository.save(condominio);
    }

    @Transactional
    public void excluirCondominio(UUID id) {
        var condominio = buscarCondominio(id);

        if (torreRepository.existsByCondominioId(id)
                || apartamentoRepository.existsByCondominioId(id)
                || areaComumRepository.existsByCondominioId(id)) {
            throw new IllegalArgumentException(
                    "Não é possível excluir o condomínio: existem torres, apartamentos ou áreas comuns vinculados.");
        }

        excluir(condominioRepository, condominio);
    }

    // ---------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------

    /**
     * Exclui e força o flush para que violações de FK (registros de outros módulos
     * apontando para a entidade) sejam capturadas aqui, e não só no commit.
     */
    private <T> void excluir(JpaRepository<T, UUID> repository, T entidade) {
        try {
            repository.delete(entidade);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Não é possível excluir: existem registros vinculados a este item.");
        }
    }
}