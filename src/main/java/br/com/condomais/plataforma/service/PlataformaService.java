package br.com.condomais.plataforma.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.auth.service.UsuarioService;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.repository.CondominioRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.plataforma.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Área do MASTER (donos do Condo+): cadastra condomínios e os administradores deles, edita nome e CNPJ,
 * ativa e desativa condomínios e troca o administrador. Não toca nos dados internos de nenhum condomínio.
 */
@Service
public class PlataformaService {

    @Autowired private CondominioRepository condominioRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<CondominioPlataformaDTO> listar(UsuarioAutenticado auth) {
        exigirMaster(auth);
        var condominios = condominioRepository.findAll(Sort.by("nome"));
        var ids = condominios.stream().map(Condominio::getId).toList();
        var adminsPorCondominio = usuarioRepository.findByCondominioIdIn(ids).stream()
                .filter(PlataformaService::isAdmin)
                .collect(Collectors.groupingBy(u -> u.getCondominio().getId()));
        return condominios.stream()
                .map(c -> CondominioPlataformaDTO.from(c, adminsPorCondominio.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CondominioPlataformaDTO buscar(UUID id, UsuarioAutenticado auth) {
        exigirMaster(auth);
        return resposta(buscarCondominio(id));
    }

    @Transactional
    public CondominioPlataformaDTO cadastrar(CadastroCondominioDTO dados, UsuarioAutenticado auth) {
        exigirMaster(auth);
        if (dados.condominio() == null || dados.admin() == null) {
            throw new IllegalArgumentException("Informe os dados do condomínio e do administrador.");
        }

        var condominio = new Condominio();
        preencherCondominio(condominio, dados.condominio());
        condominio.setStatus("ATIVO");
        condominio.setCreatedAt(LocalDateTime.now());
        condominio = condominioRepository.save(condominio);

        usuarioRepository.save(novoAdmin(condominio, dados.admin()));
        return resposta(condominio);
    }

    @Transactional
    public CondominioPlataformaDTO atualizar(UUID id, DadosCondominioDTO dados, UsuarioAutenticado auth) {
        exigirMaster(auth);
        var condominio = buscarCondominio(id);
        preencherCondominio(condominio, dados);
        return resposta(condominioRepository.save(condominio));
    }

    @Transactional
    public CondominioPlataformaDTO alterarStatus(UUID id, boolean ativo, UsuarioAutenticado auth) {
        exigirMaster(auth);
        var condominio = buscarCondominio(id);
        // Inativo bloqueia o login de todos os usuários do condomínio (UsuarioAutenticado.isEnabled); nada é apagado
        condominio.setStatus(ativo ? "ATIVO" : "INATIVO");
        return resposta(condominioRepository.save(condominio));
    }

    @Transactional
    public CondominioPlataformaDTO trocarAdmin(UUID id, TrocaAdminDTO dados, UsuarioAutenticado auth) {
        exigirMaster(auth);
        var condominio = buscarCondominio(id);

        Usuario novo;
        if (dados.usuarioId() != null) {
            // Promoção de quem já é do condomínio: administrador não tem unidade nem vínculo
            novo = usuarioRepository.findByIdAndCondominioId(dados.usuarioId(), id)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado neste condomínio."));
            novo.setPerfil("ADMIN");
            novo.setApartamento(null);
            novo.setVinculo(null);
            novo.setStatus("ATIVO");
        } else {
            novo = novoAdmin(condominio, new DadosAdminDTO(dados.nome(), dados.cpf(), dados.email(), dados.telefone()));
        }
        var adminId = usuarioRepository.save(novo).getId();

        // O condomínio fica com um administrador só: os anteriores perdem o acesso, mas continuam no histórico
        usuarioRepository.findByCondominioId(id).stream()
                .filter(u -> isAdmin(u) && !u.getId().equals(adminId))
                .forEach(u -> u.setStatus("INATIVO"));

        return resposta(condominio);
    }

    // ---------------------------------------------------------------------

    private void preencherCondominio(Condominio condominio, DadosCondominioDTO dados) {
        if (dados == null || dados.nome() == null || dados.nome().isBlank()) {
            throw new IllegalArgumentException("Informe o nome do condomínio.");
        }
        if (dados.cnpj() == null || dados.cnpj().isBlank()) {
            throw new IllegalArgumentException("Informe o CNPJ do condomínio.");
        }
        var cnpj = dados.cnpj().trim();
        var outro = condominio.getId() == null
                ? condominioRepository.findByCnpj(cnpj).isPresent()
                : condominioRepository.existsByCnpjAndIdNot(cnpj, condominio.getId());
        if (outro) {
            throw new ConflitoException("Já existe um condomínio cadastrado com este CNPJ.");
        }
        condominio.setNome(dados.nome().trim());
        condominio.setCnpj(cnpj);
    }

    private Usuario novoAdmin(Condominio condominio, DadosAdminDTO dados) {
        if (dados.nome() == null || dados.nome().isBlank()) {
            throw new IllegalArgumentException("Informe o nome do administrador.");
        }
        var cpf = UsuarioService.formatarCpf(dados.cpf());
        if (usuarioRepository.findByCpf(cpf).isPresent()) {
            throw new ConflitoException("Já existe um usuário cadastrado com este CPF.");
        }
        var admin = new Usuario();
        admin.setNome(dados.nome().trim());
        admin.setCpf(cpf);
        admin.setEmail(vazioParaNulo(dados.email()));
        admin.setTelefone(vazioParaNulo(dados.telefone()));
        admin.setPerfil("ADMIN");
        admin.setStatus("ATIVO");
        // Sem senha: o administrador a cria em /auth/primeiro-acesso, como os moradores
        admin.setSenha(null);
        admin.setCondominio(condominio);
        return admin;
    }

    private CondominioPlataformaDTO resposta(Condominio condominio) {
        var admins = usuarioRepository.findByCondominioId(condominio.getId()).stream()
                .filter(PlataformaService::isAdmin)
                .toList();
        return CondominioPlataformaDTO.from(condominio, admins);
    }

    private Condominio buscarCondominio(UUID id) {
        return condominioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Condomínio não encontrado."));
    }

    private static boolean isAdmin(Usuario usuario) {
        return "ADMIN".equals(UsuarioService.normalizarPerfil(usuario.getPerfil()));
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    // O SecurityConfig já restringe /plataforma/** ao MASTER; o service confere de novo, como os demais
    private void exigirMaster(UsuarioAutenticado auth) {
        if (!"MASTER".equals(UsuarioService.normalizarPerfil(auth.getPerfil()))) {
            throw new SecurityException("Apenas a administração da plataforma pode gerenciar condomínios.");
        }
    }
}
