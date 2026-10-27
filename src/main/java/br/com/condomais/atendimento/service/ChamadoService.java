package br.com.condomais.atendimento.service;

import br.com.condomais.atendimento.dto.*;
import br.com.condomais.atendimento.model.Chamado;
import br.com.condomais.atendimento.model.HistoricoChamado;
import br.com.condomais.atendimento.repository.CategoriaChamadoRepository;
import br.com.condomais.atendimento.repository.ChamadoRepository;
import br.com.condomais.atendimento.repository.HistoricoChamadoRepository;
import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.auth.service.UsuarioService;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Chamados do condomínio do token.
 * - Qualquer perfil do condomínio abre. O morador só aponta a própria unidade (ou nenhuma, para área comum).
 * - Administração e portaria veem todos; o morador vê os que abriu e os da unidade dele.
 * - Só a administração muda o status e registra o prestador; quem pode ver o chamado pode comentar.
 * - Toda mudança vai para o histórico (RF-CHA-012). ENCERRADO é final.
 */
@Service
public class ChamadoService {

    static final String ABERTO = "ABERTO";
    static final String ENCERRADO = "ENCERRADO";
    private static final Set<String> STATUS = Set.of(ABERTO, "EM ANALISE", "EM ATENDIMENTO", "RESOLVIDO", ENCERRADO);
    private static final Set<String> PRIORIDADES = Set.of("BAIXA", "NORMAL", "ALTA", "URGENTE");

    @Autowired private ChamadoRepository chamadoRepository;
    @Autowired private CategoriaChamadoRepository categoriaRepository;
    @Autowired private ApartamentoRepository apartamentoRepository;
    @Autowired private HistoricoChamadoRepository historicoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ChamadoResponseDTO> listar(String statusInformado, UUID categoriaId, UsuarioAutenticado auth) {
        var status = normalizarStatus(statusInformado);
        List<Chamado> chamados;
        if (isMorador(auth)) {
            var unidade = usuarioDe(auth).getApartamento();
            chamados = unidade != null
                    ? chamadoRepository.listarDoMorador(auth.getCondominioId(), auth.getId(), unidade.getId())
                    : chamadoRepository.findByCondominioIdAndSolicitanteIdOrderByDataAberturaDesc(auth.getCondominioId(), auth.getId());
        } else {
            chamados = chamadoRepository.findByCondominioIdOrderByDataAberturaDesc(auth.getCondominioId());
        }
        return chamados.stream()
                .filter(c -> status == null || status.equals(c.getStatus()))
                .filter(c -> categoriaId == null || c.getCategoria().getId().equals(categoriaId))
                .map(ChamadoResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChamadoResponseDTO buscar(UUID id, UsuarioAutenticado auth) {
        var chamado = buscarVisivel(id, auth);
        return ChamadoResponseDTO.from(chamado, historicoDe(chamado));
    }

    // ---------------------------------------------------------------------
    // Abertura, andamento e comentários
    // ---------------------------------------------------------------------

    @Transactional
    public ChamadoResponseDTO abrir(AberturaChamadoDTO dados, UsuarioAutenticado auth) {
        if (dados.categoriaId() == null) {
            throw new IllegalArgumentException("Informe a categoria do chamado.");
        }
        var solicitante = usuarioDe(auth);
        var categoria = categoriaRepository.findByIdAndCondominioId(dados.categoriaId(), auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada."));

        var chamado = new Chamado();
        chamado.setTitulo(obrigatorio(dados.titulo(), "Informe o título do chamado.", 255));
        chamado.setDescricao(obrigatorio(dados.descricao(), "Descreva o problema.", Integer.MAX_VALUE));
        chamado.setPrioridade(prioridade(dados.prioridade())); // Ex: BAIXA, NORMAL (RF-CHA-006)
        chamado.setStatus(ABERTO); // Status padrão (RF-CHA-008)
        chamado.setDataAbertura(LocalDateTime.now());
        chamado.setCategoria(categoria);
        chamado.setSolicitante(solicitante);
        chamado.setCondominio(solicitante.getCondominio());
        chamado.setImagemUrl(opcional(dados.imagemUrl(), 255)); // Anexo opcional (RN-CHA-005)

        if (dados.apartamentoId() != null) {
            // O morador só abre chamado da própria unidade; portaria e administração, de qualquer uma do condomínio
            if (isMorador(auth) && (solicitante.getApartamento() == null || !dados.apartamentoId().equals(solicitante.getApartamento().getId()))) {
                throw new RecursoNaoEncontradoException("Unidade não encontrada.");
            }
            chamado.setApartamento(apartamentoRepository.findByIdAndCondominioId(dados.apartamentoId(), auth.getCondominioId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Unidade não encontrada.")));
        }

        var salvo = chamadoRepository.save(chamado);
        // Gera o log obrigatório de histórico (RF-CHA-012)
        registrar(salvo, solicitante, "ABERTURA", "Chamado aberto no sistema.");
        return ChamadoResponseDTO.from(salvo, historicoDe(salvo));
    }

    @Transactional
    public ChamadoResponseDTO atualizar(UUID id, AtualizacaoChamadoDTO dados, UsuarioAutenticado auth) {
        if (!"ADMIN".equals(UsuarioService.normalizarPerfil(auth.getPerfil()))) {
            throw new SecurityException("Apenas a administração atualiza o andamento dos chamados.");
        }
        var chamado = buscarDoCondominio(id, auth);
        if (ENCERRADO.equals(chamado.getStatus())) {
            throw new ConflitoException("Este chamado está encerrado e não pode mais ser alterado.");
        }
        var autor = usuarioDe(auth);
        var novoStatus = normalizarStatus(dados.status());
        var mudouAlgo = false;

        if (novoStatus != null && !novoStatus.equals(chamado.getStatus())) {
            var anterior = chamado.getStatus();
            chamado.setStatus(novoStatus);
            chamado.setDataEncerramento(ENCERRADO.equals(novoStatus) ? LocalDateTime.now() : null);
            registrar(chamado, autor, "STATUS: " + anterior + " -> " + novoStatus, textoOuNulo(dados.comentario()));
            mudouAlgo = true;
        }
        // Prestador de serviço externo e previsão de atendimento: campos nulos não mudam
        if (dados.responsavelExterno() != null || dados.contatoExterno() != null || dados.dataPrevisaoAtendimento() != null) {
            if (dados.responsavelExterno() != null) chamado.setResponsavelExterno(opcional(dados.responsavelExterno(), 255));
            if (dados.contatoExterno() != null) chamado.setContatoExterno(opcional(dados.contatoExterno(), 255));
            if (dados.dataPrevisaoAtendimento() != null) chamado.setDataPrevisaoAtendimento(dados.dataPrevisaoAtendimento());
            registrar(chamado, autor, "ATENDIMENTO ATUALIZADO", mudouAlgo ? null : textoOuNulo(dados.comentario()));
            mudouAlgo = true;
        }
        if (!mudouAlgo) {
            if (textoOuNulo(dados.comentario()) == null) {
                throw new IllegalArgumentException("Informe um novo status, os dados do atendimento ou um comentário.");
            }
            registrar(chamado, autor, "COMENTARIO", dados.comentario().trim());
        }
        var salvo = chamadoRepository.save(chamado);
        return ChamadoResponseDTO.from(salvo, historicoDe(salvo));
    }

    @Transactional
    public ChamadoResponseDTO comentar(UUID id, ComentarioChamadoDTO dados, UsuarioAutenticado auth) {
        var chamado = buscarVisivel(id, auth);
        if (ENCERRADO.equals(chamado.getStatus())) {
            throw new ConflitoException("Este chamado está encerrado e não aceita novos comentários.");
        }
        var texto = obrigatorio(dados == null ? null : dados.comentario(), "Escreva o comentário.", Integer.MAX_VALUE);
        registrar(chamado, usuarioDe(auth), "COMENTARIO", texto);
        return ChamadoResponseDTO.from(chamado, historicoDe(chamado));
    }

    // ---------------------------------------------------------------------

    private Chamado buscarDoCondominio(UUID id, UsuarioAutenticado auth) {
        return chamadoRepository.findByIdAndCondominioId(id, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado não encontrado."));
    }

    // Para o morador, chamado que ele não abriu e que não é da unidade dele responde 404
    private Chamado buscarVisivel(UUID id, UsuarioAutenticado auth) {
        var chamado = buscarDoCondominio(id, auth);
        if (isMorador(auth)) {
            var morador = usuarioDe(auth);
            var abriu = chamado.getSolicitante() != null && chamado.getSolicitante().getId().equals(auth.getId());
            var daUnidade = morador.getApartamento() != null && chamado.getApartamento() != null
                    && Objects.equals(chamado.getApartamento().getId(), morador.getApartamento().getId());
            if (!abriu && !daUnidade) {
                throw new RecursoNaoEncontradoException("Chamado não encontrado.");
            }
        }
        return chamado;
    }

    private void registrar(Chamado chamado, Usuario usuario, String acao, String comentario) {
        var historico = new HistoricoChamado();
        historico.setAcao(acao);
        historico.setComentario(comentario);
        historico.setDataHora(LocalDateTime.now());
        historico.setChamado(chamado);
        historico.setUsuario(usuario);
        historicoRepository.save(historico);
    }

    private List<HistoricoChamadoDTO> historicoDe(Chamado chamado) {
        return historicoRepository.findByChamadoIdOrderByDataHoraAsc(chamado.getId()).stream()
                .map(HistoricoChamadoDTO::from).toList();
    }

    private Usuario usuarioDe(UsuarioAutenticado auth) {
        return usuarioRepository.findById(auth.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private static String prioridade(String informada) {
        var prioridade = informada == null || informada.isBlank() ? "NORMAL" : informada.trim().toUpperCase();
        if (!PRIORIDADES.contains(prioridade)) {
            throw new IllegalArgumentException("Prioridade inválida. Use BAIXA, NORMAL, ALTA ou URGENTE.");
        }
        return prioridade;
    }

    // Aceita "EM_ANALISE", "em análise" ou "EM ANALISE"; guarda sem acento
    private static String normalizarStatus(String status) {
        if (status == null || status.isBlank()) return null;
        var normalizado = java.text.Normalizer.normalize(status, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toUpperCase().replace('_', ' ');
        if (!STATUS.contains(normalizado)) {
            throw new IllegalArgumentException("Status inválido. Use ABERTO, EM_ANALISE, EM_ATENDIMENTO, RESOLVIDO ou ENCERRADO.");
        }
        return normalizado;
    }

    private static String obrigatorio(String valor, String mensagem, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        var texto = valor.trim();
        if (texto.length() > tamanhoMaximo) {
            throw new IllegalArgumentException("Texto acima de " + tamanhoMaximo + " caracteres.");
        }
        return texto;
    }

    private static String opcional(String valor, int tamanhoMaximo) {
        var texto = textoOuNulo(valor);
        if (texto != null && texto.length() > tamanhoMaximo) {
            throw new IllegalArgumentException("Texto acima de " + tamanhoMaximo + " caracteres.");
        }
        return texto;
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static boolean isMorador(UsuarioAutenticado auth) {
        return "MORADOR".equals(UsuarioService.normalizarPerfil(auth.getPerfil()));
    }
}
