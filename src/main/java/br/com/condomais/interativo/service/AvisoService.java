package br.com.condomais.interativo.service;

import br.com.condomais.atendimento.model.Chamado;
import br.com.condomais.atendimento.repository.ChamadoRepository;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.auth.service.UsuarioService;
import br.com.condomais.condominio.model.Torre;
import br.com.condomais.condominio.repository.TorreRepository;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.dto.AvisoResponseDTO;
import br.com.condomais.interativo.dto.RegistroAvisoDTO;
import br.com.condomais.interativo.model.Aviso;
import br.com.condomais.interativo.repository.AvisoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Comunicados do condomínio do token. Administração e portaria publicam, editam, excluem e veem todos,
 * inclusive os vencidos; o morador vê só os ativos (dentro da validade) que são gerais ou da torre dele.
 * Um comunicado de outro condomínio, ou que o morador não pode ver, responde 404.
 * A administração pode ligar o comunicado a um chamado, pelo campo chamadoId ou mencionando no texto o código
 * que as telas mostram (@3F2A1C9E). Um comunicado se refere a um chamado só; a portaria não liga chamados.
 */
@Service
public class AvisoService {

    // A validade é uma data do condomínio, não do servidor (o Render roda em UTC)
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Set<String> PRIORIDADES = Set.of("NORMAL", "URGENTE");
    // @ seguido dos 8 caracteres do código, fora de e-mails (nada de letra, número ou ponto antes do @)
    private static final Pattern MENCAO = Pattern.compile("(?<![\\w.])@([0-9A-Fa-f]{8})(?![\\w])");

    @Autowired
    private AvisoRepository avisoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private TorreRepository torreRepository;
    @Autowired
    private ChamadoRepository chamadoRepository;

    @Transactional(readOnly = true)
    public List<AvisoResponseDTO> listar(boolean apenasAtivos, UsuarioAutenticado auth) {
        var hoje = hoje();
        List<Aviso> avisos;
        if (isMorador(auth)) {
            var torreId = torreDoMorador(auth);
            avisos = torreId != null
                    ? avisoRepository.buscarAvisosAtivosParaMorador(auth.getCondominioId(), torreId, hoje)
                    : avisoRepository.buscarAvisosGeraisAtivos(auth.getCondominioId(), hoje);
        } else {
            avisos = avisoRepository.findByCondominioIdOrderByCreatedAtDesc(auth.getCondominioId());
        }
        return avisos.stream()
                .map(aviso -> AvisoResponseDTO.from(aviso, hoje))
                .filter(aviso -> !apenasAtivos || aviso.ativo())
                .toList();
    }

    @Transactional(readOnly = true)
    public AvisoResponseDTO buscar(UUID id, UsuarioAutenticado auth) {
        var hoje = hoje();
        var aviso = buscarDoCondominio(id, auth);
        if (isMorador(auth) && !visivelParaMorador(aviso, torreDoMorador(auth), hoje)) {
            throw new RecursoNaoEncontradoException("Comunicado não encontrado.");
        }
        return AvisoResponseDTO.from(aviso, hoje);
    }

    @Transactional
    public AvisoResponseDTO publicar(RegistroAvisoDTO dados, UsuarioAutenticado auth) {
        exigirAdminOuPortaria(auth);
        var autor = usuarioRepository.findById(auth.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Autor não encontrado."));

        var aviso = new Aviso();
        preencher(aviso, dados, auth);
        // Garante o isolamento Multi-Condomínio e vincula o autor
        aviso.setCondominio(autor.getCondominio());
        aviso.setAutor(autor);
        aviso.setCreatedAt(LocalDateTime.now(FUSO));
        return AvisoResponseDTO.from(avisoRepository.save(aviso), hoje());
    }

    @Transactional
    public AvisoResponseDTO atualizar(UUID id, RegistroAvisoDTO dados, UsuarioAutenticado auth) {
        exigirAdminOuPortaria(auth);
        var aviso = buscarDoCondominio(id, auth);
        // Autor e data de publicação não mudam na edição
        preencher(aviso, dados, auth);
        return AvisoResponseDTO.from(avisoRepository.save(aviso), hoje());
    }

    @Transactional
    public void excluir(UUID id, UsuarioAutenticado auth) {
        exigirAdminOuPortaria(auth);
        avisoRepository.delete(buscarDoCondominio(id, auth));
    }

    // ---------------------------------------------------------------------

    private void preencher(Aviso aviso, RegistroAvisoDTO dados, UsuarioAutenticado auth) {
        aviso.setTitulo(obrigatorio(dados.titulo(), "Informe o título do comunicado.", 255));
        aviso.setConteudo(obrigatorio(dados.conteudo(), "Informe o texto do comunicado.", Integer.MAX_VALUE));

        // Aplicação da regra de prioridade simplificada (V1.1); sem prioridade, o comunicado é NORMAL
        var prioridade = dados.prioridade() == null || dados.prioridade().isBlank() ? "NORMAL" : dados.prioridade().trim().toUpperCase();
        if (!PRIORIDADES.contains(prioridade)) {
            throw new IllegalArgumentException("A prioridade deve ser NORMAL ou URGENTE.");
        }
        aviso.setPrioridade(prioridade);

        // Define a validade após a qual o aviso deixará de ser ativo
        if (dados.dataValidade() != null && dados.dataValidade().isBefore(hoje())) {
            throw new IllegalArgumentException("A data de validade não pode estar no passado.");
        }
        aviso.setDataValidade(dados.dataValidade());

        // Regra de direcionamento: torre do mesmo condomínio, ou nula para o condomínio inteiro
        Torre torre = null;
        if (dados.torreId() != null) {
            torre = torreRepository.findByIdAndCondominioId(dados.torreId(), auth.getCondominioId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Torre não encontrada."));
        }
        aviso.setTorre(torre);
        aviso.setChamado(chamadoDoAviso(dados, auth));
    }

    // O escolhido no campo e/ou o mencionado no texto, que precisam ser o mesmo. Sem nenhum dos dois, desliga.
    private Chamado chamadoDoAviso(RegistroAvisoDTO dados, UsuarioAutenticado auth) {
        if ("PORTARIA".equals(UsuarioService.normalizarPerfil(auth.getPerfil()))) {
            if (dados.chamadoId() != null) {
                throw new SecurityException("A portaria não tem acesso aos chamados e não os vincula a comunicados.");
            }
            return null; // uma menção no texto da portaria fica só como texto
        }
        Chamado chamado = null;
        if (dados.chamadoId() != null) {
            chamado = chamadoRepository.findByIdAndCondominioId(dados.chamadoId(), auth.getCondominioId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado não encontrado."));
        }
        var codigos = new LinkedHashSet<String>();
        var mencoes = MENCAO.matcher(dados.conteudo());
        while (mencoes.find()) codigos.add(mencoes.group(1).toLowerCase(Locale.ROOT));
        for (var codigo : codigos) {
            var mencionado = chamadoPeloCodigo(codigo, auth);
            if (chamado != null && !chamado.getId().equals(mencionado.getId())) {
                throw new IllegalArgumentException("Um comunicado se refere a um chamado só: o texto menciona um chamado diferente do selecionado.");
            }
            chamado = mencionado;
        }
        return chamado;
    }

    private Chamado chamadoPeloCodigo(String codigo, UsuarioAutenticado auth) {
        var achados = chamadoRepository.findByCondominioId(auth.getCondominioId()).stream()
                .filter(c -> c.getId().toString().startsWith(codigo))
                .toList();
        var mencao = "@" + codigo.toUpperCase(Locale.ROOT);
        if (achados.isEmpty()) {
            throw new IllegalArgumentException("O chamado " + mencao + " não foi encontrado neste condomínio.");
        }
        if (achados.size() > 1) {
            throw new IllegalArgumentException("O código " + mencao + " corresponde a mais de um chamado; escolha no campo Chamado relacionado.");
        }
        return achados.get(0);
    }

    private Aviso buscarDoCondominio(UUID id, UsuarioAutenticado auth) {
        return avisoRepository.findByIdAndCondominioId(id, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Comunicado não encontrado."));
    }

    private static boolean visivelParaMorador(Aviso aviso, UUID torreDoMorador, LocalDate hoje) {
        var ativo = aviso.getDataValidade() == null || !aviso.getDataValidade().isBefore(hoje);
        var paraEle = aviso.getTorre() == null || aviso.getTorre().getId().equals(torreDoMorador);
        return ativo && paraEle;
    }

    // A torre vem do cadastro do morador (unidade dele), não do que o cliente envia
    private UUID torreDoMorador(UsuarioAutenticado auth) {
        return usuarioRepository.findById(auth.getId())
                .map(usuario -> usuario.getApartamento())
                .map(apartamento -> apartamento.getTorre() != null ? apartamento.getTorre().getId() : null)
                .orElse(null);
    }

    private static LocalDate hoje() {
        return LocalDate.now(FUSO);
    }

    private static String obrigatorio(String valor, String mensagem, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        var texto = valor.trim();
        if (texto.length() > tamanhoMaximo) {
            throw new IllegalArgumentException("O título deve ter no máximo " + tamanhoMaximo + " caracteres.");
        }
        return texto;
    }

    private static boolean isMorador(UsuarioAutenticado auth) {
        return "MORADOR".equals(UsuarioService.normalizarPerfil(auth.getPerfil()));
    }

    // RN-AVI-001: só administração e portaria publicam; checado aqui, como nos outros módulos
    private static void exigirAdminOuPortaria(UsuarioAutenticado auth) {
        var perfil = UsuarioService.normalizarPerfil(auth.getPerfil());
        if (!"ADMIN".equals(perfil) && !"PORTARIA".equals(perfil)) {
            throw new SecurityException("Apenas a administração ou a portaria publica e altera comunicados.");
        }
    }
}
