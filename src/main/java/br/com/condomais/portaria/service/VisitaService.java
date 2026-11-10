package br.com.condomais.portaria.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.auth.service.UsuarioService;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.portaria.dto.AutorizacaoVisitaDTO;
import br.com.condomais.portaria.dto.RegistroEntradaDTO;
import br.com.condomais.portaria.dto.VisitaResponseDTO;
import br.com.condomais.portaria.model.Visita;
import br.com.condomais.portaria.model.Visitante;
import br.com.condomais.portaria.repository.VisitaRepository;
import br.com.condomais.portaria.repository.VisitanteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Visitas do condomínio do token.
 * - O morador autoriza visitantes para a própria unidade, com período de entrada; a visita já nasce AUTORIZADO.
 * - A portaria também cadastra: AUTORIZADO quando há autorização prévia, senão PENDENTE até a portaria ou
 *   um morador da unidade autorizar ou recusar.
 * - Só a portaria registra a entrada (AUTORIZADO e dentro do período) e a saída. A entrada imediata
 *   (POST /entrada) continua para o visitante que chega sem cadastro prévio.
 * - A administração só consulta: não cadastra, não autoriza e não registra entrada nem saída (CONDO-12).
 * - PENDENTE ou AUTORIZADO com o período vencido aparece como EXPIRADO (calculado, não gravado).
 * - O período de uma autorização vai até 30 dias. O visitante é um só por CPF no condomínio: só a portaria e
 *   a administração, que conferem o documento, corrigem o nome dele; o morador não renomeia o visitante dos outros.
 * - Portaria e administração veem todas; o morador, as da própria unidade, menos as de quem já morou nela.
 * - Quando o morador sai da unidade (muda, é desativado ou deixa de ser morador), as autorizações dele ali que
 *   ainda valem viram RECUSADO: o novo ocupante não herda visitantes liberados pelo anterior (CONDO-11.4).
 */
@Service
public class VisitaService {

    static final String PENDENTE = "PENDENTE";
    static final String AUTORIZADO = "AUTORIZADO";
    static final String RECUSADO = "RECUSADO";
    static final String ENTROU = "ENTROU";
    static final String SAIU = "SAIU";
    static final String EXPIRADO = "EXPIRADO";
    private static final Set<String> STATUS = Set.of(PENDENTE, AUTORIZADO, RECUSADO, ENTROU, SAIU, EXPIRADO);
    // Horários das visitas são do condomínio, não do servidor (o Render roda em UTC)
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // Autorização é por visita, não permanente (CONDO-07): o período cobre no máximo uma estadia curta
    static final Duration PERIODO_MAXIMO = Duration.ofDays(30);

    @Autowired private VisitaRepository visitaRepository;
    @Autowired private VisitanteRepository visitanteRepository;
    @Autowired private ApartamentoRepository apartamentoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<VisitaResponseDTO> listar(String statusInformado, UUID apartamentoId, LocalDate data, UsuarioAutenticado auth) {
        var status = normalizarStatus(statusInformado);
        List<Visita> visitas;
        if (isMorador(auth)) {
            var unidade = usuarioDe(auth).getApartamento();
            if (unidade == null) return List.of(); // morador ainda sem unidade
            var morador = usuarioDe(auth);
            visitas = visitaRepository.listarDaUnidade(auth.getCondominioId(), unidade.getId()).stream()
                    .filter(v -> visivelParaMorador(v, morador))
                    .toList();
            apartamentoId = null; // o morador não escolhe a unidade
        } else {
            visitas = visitaRepository.listarDoCondominio(auth.getCondominioId());
        }
        var agora = agora();
        var unidadeFiltro = apartamentoId;
        return visitas.stream()
                .filter(v -> unidadeFiltro == null || v.getApartamento().getId().equals(unidadeFiltro))
                .filter(v -> data == null || cobre(v, data))
                .map(v -> VisitaResponseDTO.from(v, statusEfetivo(v, agora)))
                .filter(v -> status == null || status.equals(v.status()))
                .toList();
    }

    @Transactional(readOnly = true)
    public VisitaResponseDTO buscar(UUID id, UsuarioAutenticado auth) {
        return resposta(buscarVisivel(id, auth));
    }

    // ---------------------------------------------------------------------
    // Cadastro e autorização
    // ---------------------------------------------------------------------

    @Transactional
    public VisitaResponseDTO cadastrar(AutorizacaoVisitaDTO dados, UsuarioAutenticado auth) {
        var autor = usuarioDe(auth);
        Apartamento apartamento;
        Usuario responsavel;
        String status;
        String origem;
        if (isMorador(auth)) {
            apartamento = autor.getApartamento();
            if (apartamento == null) {
                throw new IllegalArgumentException("Seu cadastro não tem unidade vinculada para autorizar visitantes.");
            }
            responsavel = autor;
            status = AUTORIZADO;
            origem = "MORADOR";
        } else {
            exigirPortaria(auth);
            apartamento = apartamentoDoCondominio(dados.apartamentoId(), auth);
            responsavel = dados.moradorId() == null ? null : moradorDaUnidade(dados.moradorId(), apartamento, auth);
            status = Boolean.TRUE.equals(dados.autorizado()) ? AUTORIZADO : PENDENTE;
            origem = "PORTARIA";
        }

        var inicio = juntar(dados.dataInicio(), dados.horaInicio(), "do início");
        var fim = juntar(dados.dataFim(), dados.horaFim(), "do término");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("O término do período precisa ser depois do início.");
        }
        if (Duration.between(inicio, fim).compareTo(PERIODO_MAXIMO) > 0) {
            throw new IllegalArgumentException("Uma autorização de visita vale por no máximo 30 dias.");
        }
        var agora = agora();
        if (!fim.isAfter(agora)) {
            throw new IllegalArgumentException("O período informado já terminou.");
        }

        var visita = new Visita();
        visita.setVisitante(visitanteDoCondominio(dados.cpf(), dados.nome(), dados.telefone(), apartamento.getCondominio(), !isMorador(auth)));
        visita.setApartamento(apartamento);
        visita.setCondominio(apartamento.getCondominio());
        visita.setMorador(responsavel);
        visita.setStatus(status);
        visita.setOrigem(origem);
        visita.setObservacao(textoOuNulo(dados.observacao()));
        visita.setDataPrevista(inicio.toLocalDate());
        visita.setHorarioPrevisto(inicio.toLocalTime());
        visita.setDataFimPrevista(fim.toLocalDate());
        visita.setHorarioFimPrevisto(fim.toLocalTime());
        visita.setDataHoraRegistro(agora);
        return resposta(visitaRepository.save(visita));
    }

    /** PENDENTE → AUTORIZADO, pela portaria ou por um morador da unidade. */
    @Transactional
    public VisitaResponseDTO autorizar(UUID id, UsuarioAutenticado auth) {
        if (!isMorador(auth)) exigirPortaria(auth);
        var visita = buscarVisivel(id, auth); // o morador só alcança visitas da própria unidade
        exigirStatus(visita, Set.of(PENDENTE), "Só uma visita pendente pode ser autorizada.");
        visita.setStatus(AUTORIZADO);
        if (isMorador(auth)) visita.setMorador(usuarioDe(auth)); // quem autorizou passa a ser o responsável
        return resposta(visitaRepository.save(visita));
    }

    /** PENDENTE ou AUTORIZADO → RECUSADO (também serve para revogar uma autorização antes da entrada). */
    @Transactional
    public VisitaResponseDTO recusar(UUID id, UsuarioAutenticado auth) {
        if (!isMorador(auth)) exigirPortaria(auth);
        var visita = buscarVisivel(id, auth);
        exigirStatus(visita, Set.of(PENDENTE, AUTORIZADO), "Só uma visita pendente ou autorizada pode ser recusada.");
        visita.setStatus(RECUSADO);
        return resposta(visitaRepository.save(visita));
    }

    /**
     * O morador saiu da unidade (mudou, foi desativado ou deixou de ser morador): as visitas que ele autorizou
     * ou pelas quais respondia ali, e que ainda não aconteceram, deixam de valer. Expiradas ficam como estão.
     */
    @Transactional
    public void revogarAutorizacoesDoMorador(Usuario morador, Apartamento unidade) {
        if (morador == null || unidade == null) return;
        var agora = agora();
        var abertas = visitaRepository.listarAutorizacoesAbertas(unidade.getId(), morador.getId()).stream()
                .filter(v -> !EXPIRADO.equals(statusEfetivo(v, agora)))
                .toList();
        abertas.forEach(v -> v.setStatus(RECUSADO));
        visitaRepository.saveAll(abertas);
    }

    // ---------------------------------------------------------------------
    // Entrada e saída (só a portaria)
    // ---------------------------------------------------------------------

    /** Entrada de uma visita autorizada, dentro do período (RF-POR-006, RF-POR-014). */
    @Transactional
    public VisitaResponseDTO registrarEntrada(UUID id, UsuarioAutenticado auth) {
        exigirPortaria(auth);
        var visita = buscarDoCondominio(id, auth);
        exigirStatus(visita, Set.of(AUTORIZADO), "A entrada exige uma autorização válida.");
        var agora = agora();
        var inicio = inicio(visita);
        if (inicio != null && agora.isBefore(inicio)) {
            throw new ConflitoException("Entrada indisponível: a autorização ainda não começou.");
        }
        visita.setStatus(ENTROU);
        visita.setDataHoraEntrada(agora);
        visita.setPorteiroEntrada(usuarioDe(auth)); // Rastreabilidade (RN-POR-010)
        return resposta(visitaRepository.save(visita));
    }

    /** Visitante que chega sem cadastro prévio: cadastra e já registra a entrada. */
    @Transactional
    public VisitaResponseDTO registrarEntradaImediata(RegistroEntradaDTO dados, UsuarioAutenticado auth) {
        exigirPortaria(auth);
        var apartamento = apartamentoDoCondominio(dados.apartamentoId(), auth);
        var agora = agora();

        var visita = new Visita();
        visita.setVisitante(visitanteDoCondominio(dados.cpf(), dados.nome(), dados.telefone(), apartamento.getCondominio(), true));
        visita.setApartamento(apartamento);
        visita.setCondominio(apartamento.getCondominio());
        visita.setStatus(ENTROU);
        visita.setOrigem("PORTARIA");
        visita.setObservacao(textoOuNulo(dados.observacao()));
        visita.setDataPrevista(agora.toLocalDate());
        visita.setHorarioPrevisto(agora.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
        visita.setDataHoraRegistro(agora);
        visita.setDataHoraEntrada(agora);
        visita.setPorteiroEntrada(usuarioDe(auth));
        return resposta(visitaRepository.save(visita));
    }

    @Transactional
    public VisitaResponseDTO registrarSaida(UUID id, UsuarioAutenticado auth) {
        exigirPortaria(auth);
        var visita = buscarDoCondominio(id, auth);
        if (!ENTROU.equals(visita.getStatus())) {
            throw new ConflitoException("Só é possível registrar a saída de um visitante que entrou e ainda não saiu.");
        }
        // Atualiza status e horários (RF-POR-007)
        visita.setStatus(SAIU);
        visita.setDataHoraSaida(agora());
        visita.setPorteiroSaida(usuarioDe(auth));
        return resposta(visitaRepository.save(visita));
    }

    // ---------------------------------------------------------------------

    private Visita buscarDoCondominio(UUID id, UsuarioAutenticado auth) {
        return visitaRepository.findByIdAndCondominioId(id, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Visita não encontrada."));
    }

    private Visita buscarVisivel(UUID id, UsuarioAutenticado auth) {
        var visita = buscarDoCondominio(id, auth);
        if (isMorador(auth)) {
            var morador = usuarioDe(auth);
            var unidade = morador.getApartamento();
            if (unidade == null || !unidade.getId().equals(visita.getApartamento().getId()) || !visivelParaMorador(visita, morador)) {
                throw new RecursoNaoEncontradoException("Visita não encontrada.");
            }
        }
        return visita;
    }

    // Da unidade do morador: as cadastradas pela portaria sem responsável e as de quem ainda mora nela.
    // As do ocupante anterior (responsável que já saiu) ficam fora, com o nome e o CPF dos visitantes dele.
    private static boolean visivelParaMorador(Visita visita, Usuario morador) {
        var responsavel = visita.getMorador();
        if (responsavel == null || responsavel.getId().equals(morador.getId())) return true;
        return responsavel.getApartamento() != null && responsavel.getApartamento().getId().equals(visita.getApartamento().getId())
                && responsavel.isAtivo();
    }

    private Apartamento apartamentoDoCondominio(UUID apartamentoId, UsuarioAutenticado auth) {
        if (apartamentoId == null) {
            throw new IllegalArgumentException("Informe a unidade visitada.");
        }
        // Valida o apartamento respeitando o isolamento do condomínio (RN-CON-004)
        return apartamentoRepository.findByIdAndCondominioId(apartamentoId, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Apartamento não encontrado neste condomínio."));
    }

    private Usuario moradorDaUnidade(UUID moradorId, Apartamento apartamento, UsuarioAutenticado auth) {
        var morador = usuarioRepository.findByIdAndCondominioId(moradorId, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Morador não encontrado."));
        if (!"MORADOR".equals(UsuarioService.normalizarPerfil(morador.getPerfil()))
                || morador.getApartamento() == null || !morador.getApartamento().getId().equals(apartamento.getId())) {
            throw new IllegalArgumentException("O morador responsável precisa morar na unidade visitada.");
        }
        return morador;
    }

    // Busca o visitante pelo CPF no condomínio, ou cadastra. O cadastro é um só para todas as unidades: quem
    // não confere documento (o morador) não troca o nome nem o telefone que já estão lá, só preenche o que falta.
    private Visitante visitanteDoCondominio(String cpfInformado, String nome, String telefone, Condominio condominio, boolean podeCorrigir) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do visitante.");
        }
        var cpf = UsuarioService.formatarCpf(cpfInformado);
        var existente = visitanteRepository.findByCondominioIdAndCpf(condominio.getId(), cpf);
        var visitante = existente.orElseGet(() -> {
            var novo = new Visitante();
            novo.setCpf(cpf);
            novo.setCondominio(condominio);
            return novo;
        });
        var corrige = podeCorrigir || existente.isEmpty();
        if (corrige) visitante.setNome(nome.trim());
        var temTelefone = visitante.getTelefone() != null && !visitante.getTelefone().isBlank();
        if (telefone != null && !telefone.isBlank() && (corrige || !temTelefone)) visitante.setTelefone(telefone.trim());
        return visitanteRepository.save(visitante);
    }

    private void exigirStatus(Visita visita, Set<String> permitidos, String mensagem) {
        var status = statusEfetivo(visita, agora());
        if (EXPIRADO.equals(status)) {
            throw new ConflitoException("A autorização desta visita expirou.");
        }
        if (!permitidos.contains(status)) {
            throw new ConflitoException(mensagem);
        }
    }

    private VisitaResponseDTO resposta(Visita visita) {
        return VisitaResponseDTO.from(visita, statusEfetivo(visita, agora()));
    }

    static String statusEfetivo(Visita visita, LocalDateTime agora) {
        var fim = fim(visita);
        if ((PENDENTE.equals(visita.getStatus()) || AUTORIZADO.equals(visita.getStatus())) && fim != null && fim.isBefore(agora)) {
            return EXPIRADO;
        }
        return visita.getStatus();
    }

    // A visita aparece no dia se o período dela cobre a data (sem período, vale o dia da entrada)
    private static boolean cobre(Visita visita, LocalDate data) {
        var inicio = visita.getDataPrevista() != null ? visita.getDataPrevista()
                : visita.getDataHoraEntrada() != null ? visita.getDataHoraEntrada().toLocalDate() : null;
        if (inicio == null) return false;
        var fim = visita.getDataFimPrevista() != null ? visita.getDataFimPrevista() : inicio;
        return !data.isBefore(inicio) && !data.isAfter(fim);
    }

    private static LocalDateTime inicio(Visita visita) {
        return visita.getDataPrevista() == null || visita.getHorarioPrevisto() == null ? null
                : LocalDateTime.of(visita.getDataPrevista(), visita.getHorarioPrevisto());
    }

    private static LocalDateTime fim(Visita visita) {
        return visita.getDataFimPrevista() == null || visita.getHorarioFimPrevisto() == null ? null
                : LocalDateTime.of(visita.getDataFimPrevista(), visita.getHorarioFimPrevisto());
    }

    private static LocalDateTime juntar(LocalDate data, LocalTime hora, String oQue) {
        if (data == null || hora == null) {
            throw new IllegalArgumentException("Informe a data e o horário " + oQue + " do período.");
        }
        return LocalDateTime.of(data, hora.truncatedTo(ChronoUnit.MINUTES));
    }

    private Usuario usuarioDe(UsuarioAutenticado auth) {
        return usuarioRepository.findById(auth.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(FUSO);
    }

    private static String normalizarStatus(String status) {
        if (status == null || status.isBlank()) return null;
        var normalizado = status.trim().toUpperCase();
        if (!STATUS.contains(normalizado)) {
            throw new IllegalArgumentException("Status inválido. Use PENDENTE, AUTORIZADO, RECUSADO, ENTROU, SAIU ou EXPIRADO.");
        }
        return normalizado;
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static boolean isMorador(UsuarioAutenticado auth) {
        return "MORADOR".equals(UsuarioService.normalizarPerfil(auth.getPerfil()));
    }

    // A administração consulta, mas não opera a portaria (CONDO-12: "consultar, mas não executar")
    private static void exigirPortaria(UsuarioAutenticado auth) {
        if (!"PORTARIA".equals(UsuarioService.normalizarPerfil(auth.getPerfil()))) {
            throw new SecurityException("Apenas a portaria registra visitantes, autorizações, entradas e saídas.");
        }
    }
}
