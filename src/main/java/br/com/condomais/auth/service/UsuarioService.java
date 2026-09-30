package br.com.condomais.auth.service;

import br.com.condomais.auth.dto.UsuarioDTO;
import br.com.condomais.auth.dto.UsuarioResponseDTO;
import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.condominio.repository.CondominioRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro de usuários pela Administração. Tudo é escopado pelo condomínio do token de quem
 * chama: não há como listar ou alterar usuários de outro condomínio.
 */
@Service
public class UsuarioService {

    private static final Set<String> PERFIS = Set.of("ADMIN", "PORTARIA", "MORADOR");
    private static final Set<String> VINCULOS = Set.of("PROPRIETARIO", "INQUILINO");

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ApartamentoRepository apartamentoRepository;

    @Autowired
    private CondominioRepository condominioRepository;

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listar(UsuarioAutenticado auth) {
        exigirAdmin(auth);
        return usuarioRepository.findByCondominioIdOrderByNomeAsc(auth.getCondominioId()).stream()
                .map(UsuarioResponseDTO::from)
                .toList();
    }

    // Dados de quem está logado: qualquer perfil (o morador usa para montar a própria área)
    @Transactional(readOnly = true)
    public UsuarioResponseDTO meusDados(UsuarioAutenticado auth) {
        return UsuarioResponseDTO.from(buscarEntidade(auth.getId(), auth));
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscar(UUID id, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        return UsuarioResponseDTO.from(buscarEntidade(id, auth));
    }

    @Transactional
    public UsuarioResponseDTO cadastrar(UsuarioDTO dados, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var usuario = new Usuario();
        usuario.setCondominio(condominioRepository.getReferenceById(auth.getCondominioId()));
        usuario.setStatus("ATIVO");
        // Sem senha: o usuário a cria em /auth/primeiro-acesso
        usuario.setSenha(null);
        preencher(usuario, dados, auth);
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponseDTO atualizar(UUID id, UsuarioDTO dados, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var usuario = buscarEntidade(id, auth);
        if (usuario.getId().equals(auth.getId()) && !"ADMIN".equals(normalizarPerfil(dados.perfil()))) {
            throw new IllegalArgumentException("Você não pode remover o seu próprio perfil de administrador.");
        }
        preencher(usuario, dados, auth);
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponseDTO alterarStatus(UUID id, boolean ativo, UsuarioAutenticado auth) {
        exigirAdmin(auth);
        var usuario = buscarEntidade(id, auth);
        if (!ativo && usuario.getId().equals(auth.getId())) {
            throw new IllegalArgumentException("Você não pode desativar o seu próprio usuário.");
        }
        usuario.setStatus(ativo ? "ATIVO" : "INATIVO");
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    // ---------------------------------------------------------------------

    private void preencher(Usuario usuario, UsuarioDTO dados, UsuarioAutenticado auth) {
        if (dados.nome() == null || dados.nome().isBlank()) {
            throw new IllegalArgumentException("Informe o nome.");
        }
        var cpf = formatarCpf(dados.cpf());
        usuarioRepository.findByCpf(cpf)
                .filter(existente -> !existente.getId().equals(usuario.getId()))
                .ifPresent(existente -> { throw new ConflitoException("Já existe um usuário cadastrado com este CPF."); });

        var perfil = normalizarPerfil(dados.perfil());
        if (!PERFIS.contains(perfil)) {
            throw new IllegalArgumentException("Perfil inválido. Use ADMIN, PORTARIA ou MORADOR.");
        }

        Apartamento apartamento = null;
        String vinculo = null;
        if ("MORADOR".equals(perfil)) {
            if (dados.apartamentoId() == null) {
                throw new IllegalArgumentException("Informe a unidade do morador.");
            }
            apartamento = apartamentoRepository.findByIdAndCondominioId(dados.apartamentoId(), auth.getCondominioId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Unidade não encontrada neste condomínio."));
            vinculo = dados.vinculo() == null ? "" : dados.vinculo().trim().toUpperCase();
            if (!VINCULOS.contains(vinculo)) {
                throw new IllegalArgumentException("Vínculo inválido. Use PROPRIETARIO ou INQUILINO.");
            }
        }

        usuario.setNome(dados.nome().trim());
        usuario.setCpf(cpf);
        usuario.setEmail(vazioParaNulo(dados.email()));
        usuario.setTelefone(vazioParaNulo(dados.telefone()));
        usuario.setPerfil(perfil);
        usuario.setApartamento(apartamento);
        usuario.setVinculo(vinculo);
    }

    private Usuario buscarEntidade(UUID id, UsuarioAutenticado auth) {
        return usuarioRepository.findByIdAndCondominioId(id, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    // Enquanto não há autorização por perfil no SecurityConfig, o cadastro de usuários checa aqui
    private void exigirAdmin(UsuarioAutenticado auth) {
        var perfil = normalizarPerfil(auth.getPerfil());
        if (!"ADMIN".equals(perfil)) {
            throw new SecurityException("Apenas administradores podem gerenciar usuários.");
        }
    }

    // Aceita variações já existentes no banco (Admin, Administrador, Portaria, Porteiro...)
    static String normalizarPerfil(String perfil) {
        if (perfil == null) return "";
        var semAcento = Normalizer.normalize(perfil, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase();
        return switch (semAcento) {
            case "ADMIN", "ADMINISTRADOR", "ADMINISTRACAO" -> "ADMIN";
            case "PORTARIA", "PORTEIRO" -> "PORTARIA";
            default -> semAcento;
        };
    }

    // O login compara o CPF exatamente como gravado; o padrão do banco é 000.000.000-00
    static String formatarCpf(String cpf) {
        var digitos = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (!cpfValido(digitos)) {
            throw new IllegalArgumentException("CPF inválido.");
        }
        return digitos.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
    }

    static boolean cpfValido(String digitos) {
        if (digitos.length() != 11 || digitos.chars().distinct().count() == 1) return false;
        for (int posicao = 9; posicao <= 10; posicao++) {
            int soma = 0;
            for (int i = 0; i < posicao; i++) {
                soma += (digitos.charAt(i) - '0') * (posicao + 1 - i);
            }
            int digito = (soma * 10) % 11 % 10;
            if (digito != digitos.charAt(posicao) - '0') return false;
        }
        return true;
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
