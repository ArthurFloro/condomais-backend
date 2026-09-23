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
    // Torre
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TorreResponseDTO> listarTorres(UUID condominioId) {
        var torres = condominioId != null
                ? torreRepository.findByCondominioId(condominioId)
                : torreRepository.findAll();
        return torres.stream().map(TorreResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public TorreResponseDTO buscarTorre(UUID id) {
        return TorreResponseDTO.from(buscarTorreEntidade(id));
    }

    @Transactional
    public TorreResponseDTO atualizarTorre(UUID id, TorreDTO dados) {
        var torre = buscarTorreEntidade(id);
        // A torre não muda de condomínio: o condominioId do body é ignorado.
        torre.setNome(dados.nome());
        return TorreResponseDTO.from(torreRepository.save(torre));
    }

    @Transactional
    public void excluirTorre(UUID id) {
        var torre = buscarTorreEntidade(id);

        if (apartamentoRepository.existsByTorreId(id)) {
            throw new IllegalArgumentException("Não é possível excluir a torre: existem apartamentos vinculados.");
        }

        excluir(torreRepository, torre);
    }

    // ---------------------------------------------------------------------
    // Apartamento
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ApartamentoResponseDTO> listarApartamentos(UUID condominioId, UUID torreId) {
        List<Apartamento> apartamentos;
        if (condominioId != null && torreId != null) {
            apartamentos = apartamentoRepository.findByTorreIdAndCondominioId(torreId, condominioId);
        } else if (torreId != null) {
            apartamentos = apartamentoRepository.findByTorreId(torreId);
        } else if (condominioId != null) {
            apartamentos = apartamentoRepository.findByCondominioId(condominioId);
        } else {
            apartamentos = apartamentoRepository.findAll();
        }
        return apartamentos.stream().map(ApartamentoResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public ApartamentoResponseDTO buscarApartamento(UUID id) {
        return ApartamentoResponseDTO.from(buscarApartamentoEntidade(id));
    }

    @Transactional
    public ApartamentoResponseDTO atualizarApartamento(UUID id, ApartamentoDTO dados) {
        var apartamento = buscarApartamentoEntidade(id);

        apartamento.setNumero(dados.numero());
        apartamento.setStatus(dados.status().toUpperCase());

        // O apartamento não muda de condomínio (condominioId do body é ignorado),
        // mas pode trocar de torre desde que ela pertença ao mesmo condomínio.
        if (dados.torreId() != null) {
            var torre = torreRepository.findById(dados.torreId())
                    .orElseThrow(() -> new IllegalArgumentException("Torre não encontrada."));
            if (!torre.getCondominio().getId().equals(apartamento.getCondominio().getId())) {
                throw new IllegalArgumentException("A torre informada não pertence ao condomínio do apartamento.");
            }
            apartamento.setTorre(torre);
        } else {
            apartamento.setTorre(null);
        }

        return ApartamentoResponseDTO.from(apartamentoRepository.save(apartamento));
    }

    @Transactional
    public void excluirApartamento(UUID id) {
        excluir(apartamentoRepository, buscarApartamentoEntidade(id));
    }

    // ---------------------------------------------------------------------
    // Área comum
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AreaComumResponseDTO> listarAreasComuns(UUID condominioId, boolean apenasAtivas) {
        List<AreaComum> areas;
        if (condominioId != null) {
            areas = apenasAtivas
                    ? areaComumRepository.findByCondominioIdAndAtivoTrue(condominioId)
                    : areaComumRepository.findByCondominioId(condominioId);
        } else {
            areas = apenasAtivas ? areaComumRepository.findByAtivoTrue() : areaComumRepository.findAll();
        }
        return areas.stream().map(AreaComumResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public AreaComumResponseDTO buscarAreaComum(UUID id) {
        return AreaComumResponseDTO.from(buscarAreaComumEntidade(id));
    }

    @Transactional
    public AreaComumResponseDTO atualizarAreaComum(UUID id, AreaComumDTO dados) {
        var area = buscarAreaComumEntidade(id);

        // A área não muda de condomínio: o condominioId do body é ignorado.
        area.setNome(dados.nome());
        area.setDescricao(dados.descricao());
        area.setRegras(dados.regras());
        area.setReservavel(dados.reservavel());
        area.setExigeAprovacao(dados.exigeAprovacao());

        return AreaComumResponseDTO.from(areaComumRepository.save(area));
    }

    @Transactional
    public AreaComumResponseDTO alterarStatusAreaComum(UUID id, boolean ativo) {
        var area = buscarAreaComumEntidade(id);
        area.setAtivo(ativo);
        return AreaComumResponseDTO.from(areaComumRepository.save(area));
    }

    // ---------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------

    private AreaComum buscarAreaComumEntidade(UUID id) {
        return areaComumRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Área comum não encontrada."));
    }

    private Torre buscarTorreEntidade(UUID id) {
        return torreRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Torre não encontrada."));
    }

    private Apartamento buscarApartamentoEntidade(UUID id) {
        return apartamentoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Apartamento não encontrado."));
    }

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