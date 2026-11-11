package br.com.condomais.interativo.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.auth.service.UsuarioService;
import br.com.condomais.condominio.model.AreaComum;
import br.com.condomais.condominio.repository.AreaComumRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.dto.HorarioOcupadoDTO;
import br.com.condomais.interativo.dto.ReservaResponseDTO;
import br.com.condomais.interativo.dto.SolicitacaoReservaDTO;
import br.com.condomais.interativo.model.Reserva;
import br.com.condomais.interativo.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Reservas de áreas comuns do condomínio do token.
 * - O morador solicita. Em área que exige aprovação, o proprietário é confirmado na hora e o inquilino
 *   fica AGUARDANDO APROVACAO até o admin ou o proprietário da mesma unidade aprovar ou recusar.
 * - A reserva pode passar da meia-noite (22h às 02h, com dataFim no dia seguinte), até 24 horas; o conflito
 *   compara data e hora completas.
 * - Área com taxa (CONDO-08.5): depois da aprovação (ou de cara, se não precisa), a reserva fica AGUARDANDO
 *   PAGAMENTO, segurando o horário, até o admin confirmar o pagamento manual (vira CONFIRMADA). Sem cancelamento
 *   automático por falta de pagamento: o admin ou quem pediu cancela, liberando o horário.
 * - Área com horário de funcionamento: o período inteiro precisa caber nele (o funcionamento pode passar da
 *   meia-noite, e a madrugada pertence ao funcionamento que começou na véspera).
 * - Quem pediu (ou o admin) cancela enquanto a reserva não passou.
 * - Quem deixa de morar no condomínio (desativado, ou sem unidade) tem as reservas futuras canceladas na hora,
 *   liberando o horário (CONDO-11.5). Mudar de unidade dentro do condomínio não cancela.
 * - Administração e portaria veem todas; o morador vê as da própria unidade. A agenda de uma área
 *   (horários ocupados, sem nomes) é aberta a todos do condomínio.
 */
@Service
public class ReservaService {

    static final String CONFIRMADA = "CONFIRMADA";
    static final String AGUARDANDO = "AGUARDANDO APROVACAO";
    static final String CANCELADA = "CANCELADA";
    static final String RECUSADA = "RECUSADA";
    static final String AGUARDANDO_PAGAMENTO = "AGUARDANDO PAGAMENTO";
    private static final Set<String> STATUS = Set.of(CONFIRMADA, AGUARDANDO, AGUARDANDO_PAGAMENTO, CANCELADA, RECUSADA);
    // Datas e horários das reservas são do condomínio, não do servidor (o Render roda em UTC)
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // Passar da meia-noite não pode virar reserva de vários dias seguidos
    static final Duration DURACAO_MAXIMA = Duration.ofHours(24);

    @Autowired private ReservaRepository reservaRepository;
    @Autowired private AreaComumRepository areaComumRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listar(UUID areaId, LocalDate data, String statusInformado, UsuarioAutenticado auth) {
        var status = normalizarStatus(statusInformado);
        List<Reserva> reservas;
        if (isMorador(auth)) {
            var unidade = usuarioDe(auth).getApartamento();
            if (unidade == null) return List.of(); // morador ainda sem unidade
            reservas = reservaRepository.listarDaUnidade(auth.getCondominioId(), unidade.getId());
        } else {
            reservas = reservaRepository.listarDoCondominio(auth.getCondominioId());
        }
        return reservas.stream()
                .filter(r -> areaId == null || r.getArea().getId().equals(areaId))
                .filter(r -> data == null || cobre(r, data))
                .filter(r -> status == null || status.equals(r.getStatus()))
                .map(ReservaResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservaResponseDTO buscar(UUID id, UsuarioAutenticado auth) {
        var reserva = buscarDoCondominio(id, auth);
        if (isMorador(auth) && !mesmaUnidade(usuarioDe(auth), reserva.getMorador())) {
            throw new RecursoNaoEncontradoException("Reserva não encontrada.");
        }
        return ReservaResponseDTO.from(reserva);
    }

    @Transactional(readOnly = true)
    public List<HorarioOcupadoDTO> ocupacao(UUID areaId, LocalDate data, UsuarioAutenticado auth) {
        if (areaId == null || data == null) {
            throw new IllegalArgumentException("Informe a área e a data.");
        }
        areaComumRepository.findByIdAndCondominioId(areaId, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Área comum não encontrada."));
        return reservaRepository.listarOcupacao(auth.getCondominioId(), areaId, data).stream()
                .map(r -> new HorarioOcupadoDTO(r.getDataReserva(), r.getHorarioInicio(), dataFim(r), r.getHorarioFim(), r.getStatus()))
                .toList();
    }

    // ---------------------------------------------------------------------
    // Solicitação
    // ---------------------------------------------------------------------

    @Transactional
    public ReservaResponseDTO solicitar(SolicitacaoReservaDTO dados, UsuarioAutenticado auth) {
        // Morador, ou administrador que também mora no condomínio (reserva pela própria unidade)
        var morador = usuarioDe(auth);
        if (!UsuarioService.moraNoCondominio(morador) && !isMorador(auth)) {
            throw new SecurityException("Apenas moradores solicitam reservas.");
        }
        if (dados.areaId() == null || dados.dataReserva() == null || dados.horarioInicio() == null || dados.horarioFim() == null) {
            throw new IllegalArgumentException("Informe a área, a data e os horários de início e fim.");
        }
        // Sem dataFim a reserva termina no mesmo dia; para passar da meia-noite o cliente manda o dia seguinte
        var dataFim = dados.dataFim() != null ? dados.dataFim() : dados.dataReserva();
        var inicio = LocalDateTime.of(dados.dataReserva(), dados.horarioInicio());
        var fim = LocalDateTime.of(dataFim, dados.horarioFim());
        if (!inicio.isBefore(fim)) {
            throw new IllegalArgumentException(dados.dataFim() == null
                    ? "O horário de início deve ser antes do horário de fim. Para passar da meia-noite, informe a data de fim."
                    : "O fim da reserva deve ser depois do início.");
        }
        if (Duration.between(inicio, fim).compareTo(DURACAO_MAXIMA) > 0) {
            throw new IllegalArgumentException("Uma reserva pode durar no máximo 24 horas.");
        }
        if (inicio.isBefore(agora())) {
            throw new IllegalArgumentException("Não é possível reservar um horário que já passou.");
        }

        // A trava na área serializa pedidos simultâneos, então a checagem de conflito abaixo é confiável
        var area = areaComumRepository.findParaReserva(dados.areaId(), auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Área comum não encontrada."));

        // Área desativada (soft-delete) mantém o histórico, mas não aceita reservas novas
        if (!area.getAtivo()) {
            throw new IllegalArgumentException("Esta área comum está desativada e não aceita novas reservas.");
        }
        if (!area.getReservavel()) {
            throw new IllegalArgumentException("Esta área não está configurada como reservável (RN-RES-001).");
        }
        if (!dentroDoFuncionamento(area, inicio, fim)) {
            throw new IllegalArgumentException("Fora do horário de funcionamento da área (" + funcionamento(area) + ").");
        }
        if (reservaRepository.existeConflitoDeHorario(area.getId(), dados.dataReserva(), dados.horarioInicio(), dataFim, dados.horarioFim())) {
            throw new ConflitoException("Horário indisponível. Já existe uma reserva conflitante (RN-RES-002).");
        }

        if (morador.getApartamento() == null) {
            throw new IllegalArgumentException("Seu cadastro ainda não tem unidade; procure a administração.");
        }
        var reserva = new Reserva();
        reserva.setArea(area);
        reserva.setMorador(morador);
        reserva.setCondominio(area.getCondominio());
        reserva.setDataReserva(dados.dataReserva());
        reserva.setHorarioInicio(dados.horarioInicio());
        reserva.setDataFim(dataFim);
        reserva.setHorarioFim(dados.horarioFim());

        // Regra especial da V1.1: em área que exige aprovação, o proprietário confirma direto e o
        // inquilino aguarda o admin ou o proprietário da unidade (vínculo vem do cadastro do morador)
        var precisaAprovar = Boolean.TRUE.equals(area.getExigeAprovacao()) && !"PROPRIETARIO".equals(morador.getVinculo());
        // A taxa do momento do pedido fica gravada; sem aprovação pendente, a reserva paga já aguarda o pagamento
        reserva.setValorTaxa(area.getTaxa() != null && area.getTaxa().signum() > 0 ? area.getTaxa() : null);
        reserva.setStatus(precisaAprovar ? AGUARDANDO : depoisDaAprovacao(reserva));

        return ReservaResponseDTO.from(reservaRepository.save(reserva));
    }

    // ---------------------------------------------------------------------
    // Mudanças de status
    // ---------------------------------------------------------------------

    @Transactional
    public ReservaResponseDTO cancelar(UUID id, UsuarioAutenticado auth) {
        var reserva = buscarDoCondominio(id, auth);
        var quemPediu = reserva.getMorador() != null && reserva.getMorador().getId().equals(auth.getId());
        if (!quemPediu && !isAdmin(auth)) {
            throw new SecurityException("Só quem fez a reserva, ou a administração, pode cancelá-la.");
        }
        if (!Set.of(CONFIRMADA, AGUARDANDO, AGUARDANDO_PAGAMENTO).contains(reserva.getStatus())) {
            throw new ConflitoException("Esta reserva já está " + reserva.getStatus().toLowerCase() + ".");
        }
        exigirFutura(reserva, "cancelar");
        reserva.setStatus(CANCELADA);
        return ReservaResponseDTO.from(reservaRepository.save(reserva));
    }

    /**
     * O morador deixou de morar no condomínio: as reservas dele que ainda não começaram (confirmadas ou aguardando
     * aprovação) viram CANCELADA e o horário fica livre. As que já começaram ou passaram ficam como estão.
     */
    @Transactional
    public void cancelarFuturasDoMorador(Usuario morador) {
        if (morador == null || morador.getId() == null) return;
        var agora = agora();
        var futuras = reservaRepository.listarAtivasDoMorador(morador.getId()).stream()
                .filter(r -> LocalDateTime.of(r.getDataReserva(), r.getHorarioInicio()).isAfter(agora))
                .toList();
        futuras.forEach(r -> r.setStatus(CANCELADA));
        reservaRepository.saveAll(futuras);
    }

    @Transactional
    public ReservaResponseDTO aprovar(UUID id, UsuarioAutenticado auth) {
        return decidir(id, auth, CONFIRMADA);
    }

    @Transactional
    public ReservaResponseDTO recusar(UUID id, UsuarioAutenticado auth) {
        return decidir(id, auth, RECUSADA);
    }

    private ReservaResponseDTO decidir(UUID id, UsuarioAutenticado auth, String novoStatus) {
        var reserva = buscarDoCondominio(id, auth);
        var decisor = usuarioDe(auth);
        var proprietarioDaUnidade = isMorador(auth)
                && "PROPRIETARIO".equals(decisor.getVinculo())
                && mesmaUnidade(decisor, reserva.getMorador());
        if (!isAdmin(auth) && !proprietarioDaUnidade) {
            throw new SecurityException("Só a administração ou o proprietário da unidade aprova esta reserva.");
        }
        if (!AGUARDANDO.equals(reserva.getStatus())) {
            throw new ConflitoException("Esta reserva não está aguardando aprovação.");
        }
        exigirFutura(reserva, novoStatus.equals(CONFIRMADA) ? "aprovar" : "recusar");
        // Aprovada uma reserva paga, ela ainda aguarda o pagamento
        reserva.setStatus(CONFIRMADA.equals(novoStatus) ? depoisDaAprovacao(reserva) : novoStatus);
        reserva.setAprovador(decisor);
        return ReservaResponseDTO.from(reservaRepository.save(reserva));
    }

    /** Pagamento manual confirmado pelo admin (CONDO-08.5): AGUARDANDO PAGAMENTO vira CONFIRMADA. */
    @Transactional
    public ReservaResponseDTO confirmarPagamento(UUID id, UsuarioAutenticado auth) {
        if (!isAdmin(auth)) {
            throw new SecurityException("Só a administração confirma pagamentos.");
        }
        var reserva = buscarDoCondominio(id, auth);
        if (!AGUARDANDO_PAGAMENTO.equals(reserva.getStatus())) {
            throw new ConflitoException("Esta reserva não está aguardando pagamento.");
        }
        // Pode confirmar até o fim da reserva (o pagamento pode ser feito na hora), não depois
        if (LocalDateTime.of(dataFim(reserva), reserva.getHorarioFim()).isBefore(agora())) {
            throw new IllegalArgumentException("Esta reserva já terminou.");
        }
        reserva.setStatus(CONFIRMADA);
        reserva.setPagamentoConfirmadoPor(usuarioDe(auth));
        reserva.setPagamentoConfirmadoEm(agora());
        return ReservaResponseDTO.from(reservaRepository.save(reserva));
    }

    // ---------------------------------------------------------------------

    // O período cabe no funcionamento que começa no dia da reserva ou, se ele passa da meia-noite, no da véspera
    static boolean dentroDoFuncionamento(AreaComum area, LocalDateTime inicio, LocalDateTime fim) {
        var abertura = area.getHorarioAbertura();
        var fechamento = area.getHorarioFechamento();
        if (abertura == null || fechamento == null) return true;
        var passaDaMeiaNoite = !fechamento.isAfter(abertura);
        for (var dia : List.of(inicio.toLocalDate().minusDays(1), inicio.toLocalDate())) {
            var abre = LocalDateTime.of(dia, abertura);
            var fecha = LocalDateTime.of(passaDaMeiaNoite ? dia.plusDays(1) : dia, fechamento);
            if (!inicio.isBefore(abre) && !fim.isAfter(fecha)) return true;
        }
        return false;
    }

    // "08:00 às 22:00" ou "08:00 às 02:00 do dia seguinte"
    static String funcionamento(AreaComum area) {
        var texto = area.getHorarioAbertura() + " às " + area.getHorarioFechamento();
        return area.getHorarioFechamento().isAfter(area.getHorarioAbertura()) ? texto : texto + " do dia seguinte";
    }

    // Reserva aprovada (ou que não precisava de aprovação): confirmada se gratuita, aguardando pagamento se paga
    private static String depoisDaAprovacao(Reserva reserva) {
        return reserva.getValorTaxa() != null ? AGUARDANDO_PAGAMENTO : CONFIRMADA;
    }

    private Reserva buscarDoCondominio(UUID id, UsuarioAutenticado auth) {
        return reservaRepository.findByIdAndCondominioId(id, auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva não encontrada."));
    }

    private void exigirFutura(Reserva reserva, String acao) {
        if (LocalDateTime.of(reserva.getDataReserva(), reserva.getHorarioInicio()).isBefore(agora())) {
            throw new IllegalArgumentException("Não é possível " + acao + " uma reserva que já começou.");
        }
    }

    private Usuario usuarioDe(UsuarioAutenticado auth) {
        return usuarioRepository.findById(auth.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    // Reserva antiga (antes da coluna data_fim) termina no próprio dia
    private static LocalDate dataFim(Reserva reserva) {
        return reserva.getDataFim() != null ? reserva.getDataFim() : reserva.getDataReserva();
    }

    // A reserva aparece no dia em que começa e, se passar da meia-noite, também no dia em que termina
    private static boolean cobre(Reserva reserva, LocalDate data) {
        return !data.isBefore(reserva.getDataReserva()) && !data.isAfter(dataFim(reserva));
    }

    // Unidade comparada pelo cadastro atual dos dois moradores
    private static boolean mesmaUnidade(Usuario a, Usuario b) {
        if (a == null || b == null || a.getApartamento() == null || b.getApartamento() == null) return false;
        return Objects.equals(a.getApartamento().getId(), b.getApartamento().getId());
    }

    // Aceita "AGUARDANDO_APROVACAO" (mais fácil em query string) ou "AGUARDANDO APROVACAO"
    private static String normalizarStatus(String status) {
        if (status == null || status.isBlank()) return null;
        var normalizado = status.trim().toUpperCase().replace('_', ' ');
        if (!STATUS.contains(normalizado)) {
            throw new IllegalArgumentException("Status inválido. Use CONFIRMADA, AGUARDANDO_APROVACAO, AGUARDANDO_PAGAMENTO, CANCELADA ou RECUSADA.");
        }
        return normalizado;
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(FUSO);
    }

    private static boolean isMorador(UsuarioAutenticado auth) {
        return "MORADOR".equals(UsuarioService.normalizarPerfil(auth.getPerfil()));
    }

    private static boolean isAdmin(UsuarioAutenticado auth) {
        return "ADMIN".equals(UsuarioService.normalizarPerfil(auth.getPerfil()));
    }
}
