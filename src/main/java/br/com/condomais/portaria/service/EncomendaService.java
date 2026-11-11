package br.com.condomais.portaria.service;

import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.auth.service.UsuarioService;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.portaria.dto.EncomendaResponseDTO;
import br.com.condomais.portaria.dto.RegistroEncomendaDTO;
import br.com.condomais.portaria.dto.RetiradaEncomendaDTO;
import br.com.condomais.portaria.model.Encomenda;
import br.com.condomais.portaria.repository.EncomendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Encomendas do condomínio do token. Só a portaria registra a chegada e a retirada; ela e a administração
 * (que só consulta, CONDO-12) veem todas;
 * A encomenda pode chegar sem destinatário identificado (sem unidade, CONDO-05.1): só portaria e administração a
 * veem, localizam pelo código, e a retirada exige o código informado pela pessoa (conferido com o cadastrado) e o
 * apartamento dela (só registrado). Toda retirada guarda o nome de quem retirou.
 * o morador só consulta as da própria unidade. Uma encomenda de outro condomínio, ou de outra unidade
 * para o morador, responde 404, como se não existisse.
 */
@Service
public class EncomendaService {

    static final String AGUARDANDO = "AGUARDANDO RETIRADA";
    static final String RETIRADA = "RETIRADA";
    private static final Set<String> STATUS = Set.of(AGUARDANDO, RETIRADA);
    // Horários das encomendas são do condomínio, não do servidor (o Render roda em UTC)
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private EncomendaRepository encomendaRepository;
    @Autowired
    private ApartamentoRepository apartamentoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<EncomendaResponseDTO> listar(String statusInformado, UUID apartamentoId, String codigo, boolean semDestinatario, UsuarioAutenticado auth) {
        var status = normalizarStatus(statusInformado);
        var condominioId = auth.getCondominioId();
        var unidade = isMorador(auth) ? unidadeDoMorador(auth) : apartamentoId;
        if (isMorador(auth) && unidade == null) return List.of(); // morador ainda sem unidade

        List<Encomenda> encomendas;
        if (unidade != null && status != null) {
            encomendas = encomendaRepository.findByCondominioIdAndApartamentoIdAndStatusOrderByDataHoraRecebimentoDesc(condominioId, unidade, status);
        } else if (unidade != null) {
            encomendas = encomendaRepository.findByCondominioIdAndApartamentoIdOrderByDataHoraRecebimentoDesc(condominioId, unidade);
        } else if (status != null) {
            encomendas = encomendaRepository.findByCondominioIdAndStatusOrderByDataHoraRecebimentoDesc(condominioId, status);
        } else {
            encomendas = encomendaRepository.findByCondominioIdOrderByDataHoraRecebimentoDesc(condominioId);
        }
        // Localizar pelo código (parte dele, sem diferenciar maiúsculas e espaços) e só as sem destinatário
        var trecho = normalizarCodigo(codigo);
        return encomendas.stream()
                .filter(e -> !semDestinatario || e.getApartamento() == null)
                .filter(e -> trecho.isEmpty() || normalizarCodigo(e.getCodigoIdentificacao()).contains(trecho))
                .map(EncomendaResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EncomendaResponseDTO buscar(UUID id, UsuarioAutenticado auth) {
        return EncomendaResponseDTO.from(buscarVisivel(id, auth));
    }

    @Transactional
    public EncomendaResponseDTO registrarRecebimento(RegistroEncomendaDTO dados, UsuarioAutenticado auth) {
        exigirPortaria(auth);
        if (dados.codigoIdentificacao() == null || dados.codigoIdentificacao().isBlank()) {
            throw new IllegalArgumentException("Informe o código de identificação da encomenda.");
        }
        // Valida o apartamento e o isolamento do condomínio (RN-CON-004); sem unidade = destinatário não identificado
        var apartamento = dados.apartamentoId() == null ? null
                : apartamentoRepository.findByIdAndCondominioId(dados.apartamentoId(), auth.getCondominioId())
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Apartamento não encontrado neste condomínio."));

        var porteiro = usuarioRepository.findById(auth.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Porteiro não encontrado."));

        var encomenda = new Encomenda();
        encomenda.setApartamento(apartamento);
        encomenda.setCondominio(porteiro.getCondominio());
        encomenda.setCodigoIdentificacao(dados.codigoIdentificacao().trim());
        encomenda.setObservacao(dados.observacao());
        encomenda.setStatus(AGUARDANDO); // Status inicial simplificado
        encomenda.setDataHoraRecebimento(LocalDateTime.now(FUSO));
        encomenda.setPorteiroRecebimento(porteiro); // Rastreabilidade de quem recebeu

        // Destinatário: um morador ativo da unidade, de preferência o proprietário (Usuario.apartamento).
        // Unidade sem morador cadastrado fica sem destinatário; a encomenda continua visível para a unidade.
        // TODO: Aqui faremos a integração com o Módulo 4 para disparar a Notificação ao morador
        if (apartamento != null) {
            usuarioRepository.listarMoradoresAtivosDaUnidade(apartamento.getId()).stream()
                    .findFirst()
                    .ifPresent(encomenda::setMorador);
        }

        return EncomendaResponseDTO.from(encomendaRepository.save(encomenda));
    }

    @Transactional
    public EncomendaResponseDTO registrarRetirada(UUID encomendaId, RetiradaEncomendaDTO dados, UsuarioAutenticado auth) {
        exigirPortaria(auth);
        var retirante = dados == null || dados.retiranteNome() == null ? "" : dados.retiranteNome().trim();
        if (retirante.isEmpty()) {
            throw new IllegalArgumentException("Informe o nome de quem está retirando a encomenda.");
        }
        if (retirante.length() > 255) {
            throw new IllegalArgumentException("O nome de quem retira deve ter no máximo 255 caracteres.");
        }
        var encomenda = encomendaRepository.findByIdAndCondominioId(encomendaId, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Encomenda não encontrada."));
        if (RETIRADA.equals(encomenda.getStatus())) {
            throw new ConflitoException("Esta encomenda já foi retirada.");
        }

        var porteiro = usuarioRepository.findById(auth.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Porteiro não encontrado."));

        // Sem destinatário: a pessoa informa o código (tem que ser o da etiqueta) e o apartamento dela
        if (encomenda.getApartamento() == null) {
            if (normalizarCodigo(dados.codigoConferencia()).isEmpty()) {
                throw new IllegalArgumentException("Informe o código que a pessoa apresentou para conferir com o da encomenda.");
            }
            if (!normalizarCodigo(dados.codigoConferencia()).equals(normalizarCodigo(encomenda.getCodigoIdentificacao()))) {
                throw new IllegalArgumentException("O código informado não confere com o da encomenda. A retirada não foi registrada.");
            }
            if (dados.apartamentoInformadoId() == null) {
                throw new IllegalArgumentException("Informe o apartamento de quem está retirando.");
            }
            encomenda.setRetiranteApartamento(apartamentoRepository.findByIdAndCondominioId(dados.apartamentoInformadoId(), auth.getCondominioId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Apartamento informado não encontrado neste condomínio.")));
        }

        // Atualiza status para RETIRADA e registra a rastreabilidade (RF-POR-012, RN-POR-009)
        encomenda.setStatus(RETIRADA);
        encomenda.setRetiranteNome(retirante);
        encomenda.setDataHoraRetirada(LocalDateTime.now(FUSO));
        encomenda.setPorteiroRetirada(porteiro);

        return EncomendaResponseDTO.from(encomendaRepository.save(encomenda));
    }

    // ---------------------------------------------------------------------

    private Encomenda buscarVisivel(UUID id, UsuarioAutenticado auth) {
        var encomenda = encomendaRepository.findByIdAndCondominioId(id, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Encomenda não encontrada."));
        // Sem destinatário identificado, o morador não vê (não é de nenhuma unidade)
        if (isMorador(auth) && (encomenda.getApartamento() == null || !encomenda.getApartamento().getId().equals(unidadeDoMorador(auth)))) {
            throw new RecursoNaoEncontradoException("Encomenda não encontrada.");
        }
        return encomenda;
    }

    // A unidade vem do cadastro do morador (Usuario.apartamento), não do que o cliente envia
    private UUID unidadeDoMorador(UsuarioAutenticado auth) {
        return usuarioRepository.findById(auth.getId())
                .map(usuario -> usuario.getApartamento() != null ? usuario.getApartamento().getId() : null)
                .orElse(null);
    }

    // Código comparado sem espaços e sem diferenciar maiúsculas ("br 123" = "BR123")
    static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.replaceAll("\\s+", "").toUpperCase();
    }

    // Aceita "AGUARDANDO_RETIRADA" (mais fácil em query string) ou "AGUARDANDO RETIRADA"
    private static String normalizarStatus(String status) {
        if (status == null || status.isBlank()) return null;
        var normalizado = status.trim().toUpperCase().replace('_', ' ');
        if (!STATUS.contains(normalizado)) {
            throw new IllegalArgumentException("Status inválido. Use AGUARDANDO_RETIRADA ou RETIRADA.");
        }
        return normalizado;
    }

    private static boolean isMorador(UsuarioAutenticado auth) {
        return "MORADOR".equals(UsuarioService.normalizarPerfil(auth.getPerfil()));
    }

    // A administração consulta, mas não opera a portaria (CONDO-12: "consultar, mas não executar")
    private static void exigirPortaria(UsuarioAutenticado auth) {
        if (!"PORTARIA".equals(UsuarioService.normalizarPerfil(auth.getPerfil()))) {
            throw new SecurityException("Apenas a portaria registra o recebimento e a retirada de encomendas.");
        }
    }
}
