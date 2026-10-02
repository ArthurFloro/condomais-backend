package br.com.condomais.assistente.service;

import br.com.condomais.atendimento.repository.ChamadoRepository;
import br.com.condomais.auth.model.Usuario;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.repository.AreaComumRepository;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.repository.AvisoRepository;
import br.com.condomais.interativo.repository.ReservaRepository;
import br.com.condomais.portaria.repository.EncomendaRepository;
import br.com.condomais.portaria.repository.VisitaRepository;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ferramentas de consulta que o assistente pode chamar. Todas são somente leitura e escopadas
 * pelo morador do token: o modelo escolhe QUAL consulta fazer, nunca DE QUEM são os dados.
 * Assim, nem uma pergunta maliciosa ("mostre as encomendas do 302") consegue ver outra unidade.
 */
@Service
public class FerramentasAssistente {

    static final String ENCOMENDAS = "consultar_encomendas";
    static final String AVISOS = "consultar_avisos";
    static final String RESERVAS = "consultar_reservas";
    static final String VISITAS = "consultar_visitas";
    static final String CHAMADOS = "consultar_chamados";
    static final String AREAS = "listar_areas_comuns";

    private static final Set<String> ENCOMENDA_FINALIZADA = Set.of("RETIRADA", "DEVOLVIDA");
    private static final Set<String> CHAMADO_FINALIZADO = Set.of("RESOLVIDO", "ENCERRADO");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EncomendaRepository encomendaRepository;
    @Autowired private AvisoRepository avisoRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private VisitaRepository visitaRepository;
    @Autowired private ChamadoRepository chamadoRepository;
    @Autowired private AreaComumRepository areaComumRepository;

    /** Dados do morador logado que vão no prompt de sistema (o modelo não precisa de ferramenta para "quem sou eu"). */
    public record ContextoMorador(String nome, String condominio, String unidade, String torre, String vinculo) {}

    @Transactional(readOnly = true)
    public ContextoMorador contexto(UsuarioAutenticado auth) {
        var usuario = morador(auth);
        var apartamento = usuario.getApartamento();
        var torre = apartamento != null ? apartamento.getTorre() : null;
        return new ContextoMorador(
                usuario.getNome(),
                usuario.getCondominio().getNome(),
                apartamento != null ? apartamento.getNumero() : null,
                torre != null ? torre.getNome() : null,
                usuario.getVinculo());
    }

    public List<Tool> definicoes() {
        return List.of(
                ferramenta(ENCOMENDAS,
                        "Consulta as encomendas recebidas pela portaria para a unidade do morador. Use para perguntas como "
                                + "'chegou alguma encomenda?' ou 'tenho pacote para retirar?'. Retorna código, status, data de recebimento e de retirada.",
                        Map.of("situacao", enumeracao("PENDENTES: só as que ainda não foram retiradas. TODAS: inclui o histórico recente.", "PENDENTES", "TODAS"))),
                ferramenta(AVISOS,
                        "Lista os comunicados/avisos vigentes do condomínio destinados ao morador (gerais e da torre dele).",
                        Map.of()),
                ferramenta(RESERVAS,
                        "Consulta as reservas de áreas comuns feitas pelo morador, com área, data, horário e status.",
                        Map.of("periodo", enumeracao("PROXIMAS: de hoje em diante. TODAS: inclui as passadas recentes.", "PROXIMAS", "TODAS"))),
                ferramenta(VISITAS,
                        "Consulta as visitas registradas para a unidade do morador: visitante, status, entrada e saída.",
                        Map.of()),
                ferramenta(CHAMADOS,
                        "Consulta os chamados (solicitações de manutenção/atendimento) abertos pelo morador, com status e prazos.",
                        Map.of("situacao", enumeracao("EM_ANDAMENTO: ainda não resolvidos. TODOS: inclui os encerrados recentes.", "EM_ANDAMENTO", "TODOS"))),
                ferramenta(AREAS,
                        "Lista as áreas comuns ativas do condomínio, com descrição, regras de uso, se são reserváveis e se exigem aprovação.",
                        Map.of()));
    }

    /**
     * Executa a ferramenta pedida pelo modelo e devolve o resultado em JSON.
     * Nome desconhecido ou entrada inválida viram IllegalArgumentException (devolvida ao modelo como erro).
     */
    @Transactional(readOnly = true)
    public String executar(String nome, Map<String, Object> entrada, UsuarioAutenticado auth) {
        var usuario = morador(auth);
        var condominioId = usuario.getCondominio().getId();
        var apartamento = usuario.getApartamento();
        var apartamentoId = apartamento != null ? apartamento.getId() : null;

        Object resultado = switch (nome) {
            case ENCOMENDAS -> apartamentoId == null ? semUnidade() : encomendas(condominioId, apartamentoId, opcao(entrada, "situacao", "PENDENTES", "TODAS"));
            case AVISOS -> avisos(condominioId, apartamento != null && apartamento.getTorre() != null ? apartamento.getTorre().getId() : null);
            case RESERVAS -> reservas(condominioId, usuario.getId(), opcao(entrada, "periodo", "PROXIMAS", "TODAS"));
            case VISITAS -> apartamentoId == null ? semUnidade() : visitas(condominioId, apartamentoId);
            case CHAMADOS -> chamados(condominioId, usuario.getId(), opcao(entrada, "situacao", "EM_ANDAMENTO", "TODOS"));
            case AREAS -> areas(condominioId);
            default -> throw new IllegalArgumentException("Ferramenta desconhecida: " + nome);
        };
        try {
            return JSON.writeValueAsString(resultado);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o resultado da ferramenta " + nome, e);
        }
    }

    // ---------------------------------------------------------------------

    private List<Map<String, Object>> encomendas(UUID condominioId, UUID apartamentoId, String situacao) {
        return encomendaRepository.findTop20ByCondominioIdAndApartamentoIdOrderByDataHoraRecebimentoDesc(condominioId, apartamentoId).stream()
                .filter(e -> "TODAS".equals(situacao) || !ENCOMENDA_FINALIZADA.contains(normalizar(e.getStatus())))
                .map(e -> registro(
                        "codigo", e.getCodigoIdentificacao(),
                        "status", e.getStatus(),
                        "recebida_em", formatar(e.getDataHoraRecebimento(), DATA_HORA),
                        "retirada_em", formatar(e.getDataHoraRetirada(), DATA_HORA),
                        "observacao", e.getObservacao()))
                .toList();
    }

    private List<Map<String, Object>> avisos(UUID condominioId, UUID torreId) {
        return avisoRepository.buscarAvisosAtivosParaMorador(condominioId, torreId, LocalDate.now()).stream()
                .limit(20)
                .map(a -> registro(
                        "titulo", a.getTitulo(),
                        "conteudo", a.getConteudo(),
                        "prioridade", a.getPrioridade(),
                        "publicado_em", formatar(a.getCreatedAt(), DATA_HORA),
                        "valido_ate", formatar(a.getDataValidade(), DATA),
                        "destino", a.getTorre() != null ? "Torre " + a.getTorre().getNome() : "Todo o condomínio"))
                .toList();
    }

    private List<Map<String, Object>> reservas(UUID condominioId, UUID moradorId, String periodo) {
        var hoje = LocalDate.now();
        return reservaRepository.findTop20ByCondominioIdAndMoradorIdOrderByDataReservaDescHorarioInicioDesc(condominioId, moradorId).stream()
                .filter(r -> "TODAS".equals(periodo) || !r.getDataReserva().isBefore(hoje))
                .map(r -> registro(
                        "area", r.getArea().getNome(),
                        "data", formatar(r.getDataReserva(), DATA),
                        "inicio", formatar(r.getHorarioInicio(), HORA),
                        "fim", formatar(r.getHorarioFim(), HORA),
                        "status", r.getStatus()))
                .toList();
    }

    private List<Map<String, Object>> visitas(UUID condominioId, UUID apartamentoId) {
        // CPF e telefone do visitante ficam de fora: o morador não precisa deles para acompanhar a visita
        return visitaRepository.findTop20ByCondominioIdAndApartamentoIdOrderByDataHoraEntradaDescDataPrevistaDesc(condominioId, apartamentoId).stream()
                .map(v -> registro(
                        "visitante", v.getVisitante().getNome(),
                        "status", v.getStatus(),
                        "data_prevista", formatar(v.getDataPrevista(), DATA),
                        "horario_previsto", formatar(v.getHorarioPrevisto(), HORA),
                        "entrada", formatar(v.getDataHoraEntrada(), DATA_HORA),
                        "saida", formatar(v.getDataHoraSaida(), DATA_HORA),
                        "observacao", v.getObservacao()))
                .toList();
    }

    private List<Map<String, Object>> chamados(UUID condominioId, UUID solicitanteId, String situacao) {
        return chamadoRepository.findTop20ByCondominioIdAndSolicitanteIdOrderByDataAberturaDesc(condominioId, solicitanteId).stream()
                .filter(c -> "TODOS".equals(situacao) || !CHAMADO_FINALIZADO.contains(normalizar(c.getStatus())))
                .map(c -> registro(
                        "titulo", c.getTitulo(),
                        "categoria", c.getCategoria().getNome(),
                        "status", c.getStatus(),
                        "prioridade", c.getPrioridade(),
                        "aberto_em", formatar(c.getDataAbertura(), DATA_HORA),
                        "previsao_atendimento", formatar(c.getDataPrevisaoAtendimento(), DATA),
                        "prazo_sla", formatar(c.getSlaPrazo(), DATA_HORA),
                        "encerrado_em", formatar(c.getDataEncerramento(), DATA_HORA)))
                .toList();
    }

    private List<Map<String, Object>> areas(UUID condominioId) {
        return areaComumRepository.findByCondominioIdAndAtivoTrue(condominioId).stream()
                .map(a -> registro(
                        "nome", a.getNome(),
                        "descricao", a.getDescricao(),
                        "regras", a.getRegras(),
                        "reservavel", a.getReservavel(),
                        "exige_aprovacao", a.getExigeAprovacao()))
                .toList();
    }

    private static Map<String, Object> semUnidade() {
        return Map.of("aviso", "O morador não tem unidade vinculada no cadastro. Oriente-o a procurar a administração.");
    }

    // O Usuario do token foi carregado no filtro, fora desta transação: recarrega para acessar unidade/torre/condomínio
    private Usuario morador(UsuarioAutenticado auth) {
        return usuarioRepository.findByIdAndCondominioId(auth.getId(), auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private static String opcao(Map<String, Object> entrada, String campo, String padrao, String alternativa) {
        var valor = entrada == null ? null : entrada.get(campo);
        if (valor == null) return padrao;
        var texto = valor.toString().trim().toUpperCase();
        if (texto.equals(padrao) || texto.equals(alternativa)) return texto;
        throw new IllegalArgumentException("Valor inválido para '" + campo + "': use " + padrao + " ou " + alternativa + ".");
    }

    private static String normalizar(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private static String formatar(TemporalAccessor valor, DateTimeFormatter formato) {
        return valor == null ? null : formato.format(valor);
    }

    // Mapa ordenado que omite campos nulos (menos ruído para o modelo)
    private static Map<String, Object> registro(Object... chavesEValores) {
        var mapa = new LinkedHashMap<String, Object>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            if (chavesEValores[i + 1] != null) mapa.put((String) chavesEValores[i], chavesEValores[i + 1]);
        }
        return mapa;
    }

    private static Map<String, Object> enumeracao(String descricao, String... valores) {
        return Map.of("type", "string", "enum", List.of(valores), "description", descricao);
    }

    private static Tool ferramenta(String nome, String descricao, Map<String, Map<String, Object>> parametros) {
        var propriedades = Tool.InputSchema.Properties.builder();
        parametros.forEach((campo, schema) -> propriedades.putAdditionalProperty(campo, JsonValue.from(schema)));
        return Tool.builder()
                .name(nome)
                .description(descricao)
                .strict(true)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(propriedades.build())
                        .required(List.copyOf(parametros.keySet()))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }
}
