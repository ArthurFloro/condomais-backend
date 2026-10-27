package br.com.condomais.atendimento.service;

import br.com.condomais.atendimento.dto.CategoriaChamadoDTO;
import br.com.condomais.atendimento.dto.CategoriaChamadoResponseDTO;
import br.com.condomais.atendimento.model.CategoriaChamado;
import br.com.condomais.atendimento.repository.CategoriaChamadoRepository;
import br.com.condomais.atendimento.repository.ChamadoRepository;
import br.com.condomais.auth.service.UsuarioService;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.repository.CondominioRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Categorias de chamado do condomínio do token (ex.: Elétrica, Hidráulica). Todos os perfis do condomínio
 * consultam, para escolher ao abrir um chamado; só a administração cadastra, renomeia e exclui.
 *
 * Todo condomínio tem a categoria "Outros", para o chamado que não se encaixa em nenhuma: ela é criada no
 * Setup Wizard (ou na primeira consulta, para condomínios antigos) e não pode ser renomeada nem excluída.
 */
@Service
public class CategoriaChamadoService {

    @Autowired private CategoriaChamadoRepository categoriaRepository;
    @Autowired private ChamadoRepository chamadoRepository;
    @Autowired private CondominioRepository condominioRepository;

    public static final String OUTROS = "Outros";

    @Transactional
    public List<CategoriaChamadoResponseDTO> listar(UsuarioAutenticado auth) {
        garantirOutros(condominioRepository.getReferenceById(auth.getCondominioId()));
        return categoriaRepository.findByCondominioIdOrderByNomeAsc(auth.getCondominioId()).stream()
                .map(CategoriaChamadoResponseDTO::from).toList();
    }

    /** Devolve a categoria "Outros" do condomínio, criando-a se ainda não existir. */
    @Transactional
    public CategoriaChamado garantirOutros(Condominio condominio) {
        return categoriaRepository.findFirstByCondominioIdAndNomeIgnoreCase(condominio.getId(), OUTROS)
                .orElseGet(() -> {
                    var outros = new CategoriaChamado();
                    outros.setNome(OUTROS);
                    outros.setCondominio(condominio);
                    return categoriaRepository.save(outros);
                });
    }

    @Transactional
    public CategoriaChamadoResponseDTO cadastrar(CategoriaChamadoDTO dados, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var nome = nomeValido(dados);
        if (categoriaRepository.existsByCondominioIdAndNomeIgnoreCase(auth.getCondominioId(), nome)) {
            throw new ConflitoException("Já existe uma categoria com este nome.");
        }
        var categoria = new CategoriaChamado();
        categoria.setNome(nome);
        categoria.setCondominio(condominioRepository.getReferenceById(auth.getCondominioId()));
        return CategoriaChamadoResponseDTO.from(categoriaRepository.save(categoria));
    }

    @Transactional
    public CategoriaChamadoResponseDTO renomear(UUID id, CategoriaChamadoDTO dados, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var categoria = buscar(id, auth);
        exigirNaoOutros(categoria, "renomeada");
        var nome = nomeValido(dados);
        if (categoriaRepository.existsByCondominioIdAndNomeIgnoreCaseAndIdNot(auth.getCondominioId(), nome, id)) {
            throw new ConflitoException("Já existe uma categoria com este nome.");
        }
        categoria.setNome(nome);
        return CategoriaChamadoResponseDTO.from(categoriaRepository.save(categoria));
    }

    @Transactional
    public void excluir(UUID id, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var categoria = buscar(id, auth);
        exigirNaoOutros(categoria, "excluída");
        // O chamado guarda a categoria: excluir apagaria a referência do histórico
        if (chamadoRepository.existsByCategoriaId(id)) {
            throw new ConflitoException("Não é possível excluir: existem chamados nesta categoria. Renomeie-a, se precisar.");
        }
        categoriaRepository.delete(categoria);
    }

    private CategoriaChamado buscar(UUID id, UsuarioAutenticado auth) {
        return categoriaRepository.findByIdAndCondominioId(id, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada."));
    }

    private static void exigirNaoOutros(CategoriaChamado categoria, String acao) {
        if (OUTROS.equalsIgnoreCase(categoria.getNome())) {
            throw new ConflitoException("A categoria \"Outros\" é padrão do sistema e não pode ser " + acao + ".");
        }
    }

    private static String nomeValido(CategoriaChamadoDTO dados) {
        if (dados == null || dados.nome() == null || dados.nome().isBlank()) {
            throw new IllegalArgumentException("Informe o nome da categoria.");
        }
        var nome = dados.nome().trim();
        if (nome.length() > 255) {
            throw new IllegalArgumentException("O nome da categoria deve ter no máximo 255 caracteres.");
        }
        return nome;
    }

    private static void exigirAdmin(UsuarioAutenticado auth) {
        if (!"ADMIN".equals(UsuarioService.normalizarPerfil(auth.getPerfil()))) {
            throw new SecurityException("Apenas a administração gerencia as categorias de chamado.");
        }
    }
}
