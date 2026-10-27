package br.com.condomais.interativo.service;

import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.auth.service.UsuarioService;
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
 * - Quem pediu (ou o admin) cancela enquanto a reserva não passou.
 * - Administração e portaria veem todas; o morador vê as da própria unidade. A agenda de uma área
 *   (horários ocupados, sem nomes) é aberta a todos do condomínio.
 */
@Service
public class ReservaService {

    static final String CONFIRMADA = "CONFIRMADA";
    static final String AGUARDANDO = "AGUARDANDO APROVACAO";
    static final String CANCELADA = "CANCELADA";
    static final String RECUSADA = "RECUSADA";
    private static final Set<String> STATUS = Set.of(CONFIRMADA, AGUARDANDO, CANCELADA, RECUSADA);
    // Datas e horários das reservas são do condomínio, não do servidor (o Render roda em UTC)
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

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
                .filter(r -> data == null || r.getDataReserva().equals(data))
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
                .map(r -> new HorarioOcupadoDTO(r.getHorarioInicio(), r.getHorarioFim(), r.getStatus()))
                .toList();
    }

    // ---------------------------------------------------------------------
    // Solicitação
    // ---------------------------------------------------------------------

    @Transactional
    public ReservaResponseDTO solicitar(SolicitacaoReservaDTO dados, UsuarioAutenticado auth) {
        if (!isMorador(auth)) {
            throw new SecurityException("Apenas moradores solicitam reservas.");
        }
        if (dados.areaId() == null || dados.dataReserva() == null || dados.horarioInicio() == null || dados.horarioFim() == null) {
            throw new IllegalArgumentException("Informe a área, a data e os horários de início e fim.");
        }
        if (!dados.horarioInicio().isBefore(dados.horarioFim())) {
            throw new IllegalArgumentException("O horário de início deve ser antes do horário de fim.");
        }
        if (LocalDateTime.of(dados.dataReserva(), dados.horarioInicio()).isBefore(agora())) {
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
        if (reservaRepository.existeConflitoDeHorario(area.getId(), dados.dataReserva(), dados.horarioInicio(), dados.horarioFim())) {
            throw new ConflitoException("Horário indisponível. Já existe uma reserva conflitante (RN-RES-002).");
        }

        var morador = usuarioDe(auth);
        if (morador.getApartamento() == null) {
            throw new IllegalArgumentException("Seu cadastro ainda não tem unidade; procure a administração.");
        }
        var reserva = new Reserva();
        reserva.setArea(area);
        reserva.setMorador(morador);
        reserva.setCondominio(area.getCondominio());
        reserva.setDataReserva(dados.dataReserva());
        reserva.setHorarioInicio(dados.horarioInicio());
        reserva.setHorarioFim(dados.horarioFim());

        // Regra especial da V1.1: em área que exige aprovação, o proprietário confirma direto e o
        // inquilino aguarda o admin ou o proprietário da unidade (vínculo vem do cadastro do morador)
        var precisaAprovar = Boolean.TRUE.equals(area.getExigeAprovacao()) && !"PROPRIETARIO".equals(morador.getVinculo());
        reserva.setStatus(precisaAprovar ? AGUARDANDO : CONFIRMADA);

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
        if (!CONFIRMADA.equals(reserva.getStatus()) && !AGUARDANDO.equals(reserva.getStatus())) {
            throw new ConflitoException("Esta reserva já está " + reserva.getStatus().toLowerCase() + ".");
        }
        exigirFutura(reserva, "cancelar");
        reserva.setStatus(CANCELADA);
        return ReservaResponseDTO.from(reservaRepository.save(reserva));
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
        reserva.setStatus(novoStatus);
        reserva.setAprovador(decisor);
        return ReservaResponseDTO.from(reservaRepository.save(reserva));
    }

    // ---------------------------------------------------------------------

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
            throw new IllegalArgumentException("Status inválido. Use CONFIRMADA, AGUARDANDO_APROVACAO, CANCELADA ou RECUSADA.");
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
