package br.com.condomais.condominio.service;

import br.com.condomais.auth.service.UsuarioService;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.condominio.dto.*;
import br.com.condomais.condominio.model.*;
import br.com.condomais.condominio.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Estrutura do condomínio. Tudo é escopado pelo condomínio do token: um registro de outro condomínio
 * responde 404, como se não existisse, e o condominioId enviado pelo cliente é ignorado. Leitura é
 * liberada para qualquer perfil do condomínio; cadastro, edição e exclusão exigem ADMIN. O condomínio em si
 * (cadastro, nome, CNPJ, ativação) é gerido só pelo MASTER, em PlataformaService.
 */
@Service
public class CondominioService {

    @Autowired private CondominioRepository condominioRepository;
    @Autowired private TorreRepository torreRepository;
    @Autowired private ApartamentoRepository apartamentoRepository;
    @Autowired private AreaComumRepository areaComumRepository;

    @Transactional
    public Torre cadastrarTorre(TorreDTO dados, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        if (torreRepository.existsByCondominioIdAndNomeIgnoreCase(auth.getCondominioId(), dados.nome())) {
            throw new ConflitoException("Já existe uma torre com este nome neste condomínio.");
        }
        var torre = new Torre();
        torre.setNome(dados.nome());
        torre.setCondominio(buscarCondominioDoUsuario(auth));
        return torreRepository.save(torre);
    }

    @Transactional
    public Apartamento cadastrarApartamento(ApartamentoDTO dados, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var apartamento = new Apartamento();
        apartamento.setNumero(dados.numero());
        apartamento.setStatus(dados.status().toUpperCase());
        apartamento.setCondominio(buscarCondominioDoUsuario(auth));

        if (dados.torreId() != null) {
            apartamento.setTorre(buscarTorreEntidade(dados.torreId(), auth));
        }
        exigirNumeroLivre(apartamento, null);

        return apartamentoRepository.save(apartamento);
    }

    @Transactional
    public AreaComum cadastrarAreaComum(AreaComumDTO dados, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var area = new AreaComum();
        area.setNome(dados.nome());
        area.setDescricao(dados.descricao());
        area.setRegras(dados.regras());
        area.setReservavel(dados.reservavel());
        area.setExigeAprovacao(dados.exigeAprovacao());
        area.setCondominio(buscarCondominioDoUsuario(auth));

        return areaComumRepository.save(area);
    }

    // ---------------------------------------------------------------------
    // Condomínio
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Condominio> listarCondominios(UsuarioAutenticado auth) {
        return List.of(buscarCondominioDoUsuario(auth));
    }

    @Transactional(readOnly = true)
    public Condominio buscarCondominio(UUID id, UsuarioAutenticado auth) {
        if (!id.equals(auth.getCondominioId())) {
            throw new RecursoNaoEncontradoException("Condomínio não encontrado.");
        }
        return buscarCondominioDoUsuario(auth);
    }

    // ---------------------------------------------------------------------
    // Torre
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TorreResponseDTO> listarTorres(UsuarioAutenticado auth) {
        return torreRepository.findByCondominioId(auth.getCondominioId()).stream()
                .map(TorreResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public TorreResponseDTO buscarTorre(UUID id, UsuarioAutenticado auth) {
        return TorreResponseDTO.from(buscarTorreEntidade(id, auth));
    }

    @Transactional
    public TorreResponseDTO atualizarTorre(UUID id, TorreDTO dados, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var torre = buscarTorreEntidade(id, auth);
        // A torre não muda de condomínio: o condominioId do body é ignorado.
        if (torreRepository.existsByCondominioIdAndNomeIgnoreCaseAndIdNot(auth.getCondominioId(), dados.nome(), id)) {
            throw new ConflitoException("Já existe uma torre com este nome neste condomínio.");
        }
        torre.setNome(dados.nome());
        return TorreResponseDTO.from(torreRepository.save(torre));
    }

    @Transactional
    public void excluirTorre(UUID id, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var torre = buscarTorreEntidade(id, auth);

        if (apartamentoRepository.existsByTorreId(id)) {
            throw new ConflitoException("Não é possível excluir a torre: existem apartamentos vinculados.");
        }

        excluir(torreRepository, torre);
    }

    // ---------------------------------------------------------------------
    // Apartamento
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ApartamentoResponseDTO> listarApartamentos(UUID torreId, UsuarioAutenticado auth) {
        var apartamentos = torreId != null
                ? apartamentoRepository.findByTorreIdAndCondominioId(torreId, auth.getCondominioId())
                : apartamentoRepository.findByCondominioId(auth.getCondominioId());
        return apartamentos.stream().map(ApartamentoResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public ApartamentoResponseDTO buscarApartamento(UUID id, UsuarioAutenticado auth) {
        return ApartamentoResponseDTO.from(buscarApartamentoEntidade(id, auth));
    }

    @Transactional
    public ApartamentoResponseDTO atualizarApartamento(UUID id, ApartamentoDTO dados, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var apartamento = buscarApartamentoEntidade(id, auth);

        apartamento.setNumero(dados.numero());
        apartamento.setStatus(dados.status().toUpperCase());

        // O apartamento não muda de condomínio (condominioId do body é ignorado),
        // mas pode trocar de torre desde que ela pertença ao mesmo condomínio.
        apartamento.setTorre(dados.torreId() != null ? buscarTorreEntidade(dados.torreId(), auth) : null);
        exigirNumeroLivre(apartamento, id);

        return ApartamentoResponseDTO.from(apartamentoRepository.save(apartamento));
    }

    @Transactional
    public void excluirApartamento(UUID id, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        excluir(apartamentoRepository, buscarApartamentoEntidade(id, auth));
    }

    // ---------------------------------------------------------------------
    // Área comum
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AreaComumResponseDTO> listarAreasComuns(boolean apenasAtivas, UsuarioAutenticado auth) {
        var areas = apenasAtivas
                ? areaComumRepository.findByCondominioIdAndAtivoTrue(auth.getCondominioId())
                : areaComumRepository.findByCondominioId(auth.getCondominioId());
        return areas.stream().map(AreaComumResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public AreaComumResponseDTO buscarAreaComum(UUID id, UsuarioAutenticado auth) {
        return AreaComumResponseDTO.from(buscarAreaComumEntidade(id, auth));
    }

    @Transactional
    public AreaComumResponseDTO atualizarAreaComum(UUID id, AreaComumDTO dados, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var area = buscarAreaComumEntidade(id, auth);

        // A área não muda de condomínio: o condominioId do body é ignorado.
        area.setNome(dados.nome());
        area.setDescricao(dados.descricao());
        area.setRegras(dados.regras());
        area.setReservavel(dados.reservavel());
        area.setExigeAprovacao(dados.exigeAprovacao());

        return AreaComumResponseDTO.from(areaComumRepository.save(area));
    }

    @Transactional
    public AreaComumResponseDTO alterarStatusAreaComum(UUID id, boolean ativo, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var area = buscarAreaComumEntidade(id, auth);
        area.setAtivo(ativo);
        return AreaComumResponseDTO.from(areaComumRepository.save(area));
    }

    // ---------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------

    private Condominio buscarCondominioDoUsuario(UsuarioAutenticado auth) {
        return condominioRepository.findById(auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Condomínio não encontrado."));
    }

    private AreaComum buscarAreaComumEntidade(UUID id, UsuarioAutenticado auth) {
        return areaComumRepository.findByIdAndCondominioId(id, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Área comum não encontrada."));
    }

    private Torre buscarTorreEntidade(UUID id, UsuarioAutenticado auth) {
        return torreRepository.findByIdAndCondominioId(id, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Torre não encontrada."));
    }

    private Apartamento buscarApartamentoEntidade(UUID id, UsuarioAutenticado auth) {
        return apartamentoRepository.findByIdAndCondominioId(id, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Apartamento não encontrado."));
    }

    // Número único na torre; sem torre (edifício pequeno), único entre as unidades sem torre do condomínio.
    // O banco tem índices únicos equivalentes; aqui a resposta vira um 409 legível em vez de erro 500.
    private void exigirNumeroLivre(Apartamento apartamento, UUID idAtual) {
        var numero = apartamento.getNumero();
        boolean repetido;
        if (apartamento.getTorre() != null) {
            var torreId = apartamento.getTorre().getId();
            repetido = idAtual == null
                    ? apartamentoRepository.existsByTorreIdAndNumero(torreId, numero)
                    : apartamentoRepository.existsByTorreIdAndNumeroAndIdNot(torreId, numero, idAtual);
        } else {
            var condominioId = apartamento.getCondominio().getId();
            repetido = idAtual == null
                    ? apartamentoRepository.existsByCondominioIdAndTorreIsNullAndNumero(condominioId, numero)
                    : apartamentoRepository.existsByCondominioIdAndTorreIsNullAndNumeroAndIdNot(condominioId, numero, idAtual);
        }
        if (repetido) {
            throw new ConflitoException("Já existe uma unidade com este número" + (apartamento.getTorre() != null ? " nesta torre." : "."));
        }
    }

    // Enquanto não há autorização por perfil no SecurityConfig, a estrutura do condomínio checa aqui
    private void exigirAdmin(UsuarioAutenticado auth) {
        if (!"ADMIN".equals(UsuarioService.normalizarPerfil(auth.getPerfil()))) {
            throw new SecurityException("Apenas administradores podem alterar a estrutura do condomínio.");
        }
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
            throw new ConflitoException("Não é possível excluir: existem registros vinculados a este item.");
        }
    }
}
