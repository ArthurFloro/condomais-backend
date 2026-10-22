package br.com.condomais.condominio.service;

import br.com.condomais.auth.service.UsuarioService;
import br.com.condomais.condominio.dto.ConfiguracaoInicialDTO;
import br.com.condomais.condominio.dto.ConfiguracaoInicialResponseDTO;
import br.com.condomais.condominio.model.Apartamento;
import br.com.condomais.condominio.model.AreaComum;
import br.com.condomais.condominio.model.Condominio;
import br.com.condomais.condominio.model.Torre;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.condominio.repository.AreaComumRepository;
import br.com.condomais.condominio.repository.CondominioRepository;
import br.com.condomais.condominio.repository.TorreRepository;
import br.com.condomais.core.exception.ConflitoException;
import br.com.condomais.core.exception.RecursoNaoEncontradoException;
import br.com.condomais.core.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Setup Wizard: o ADMIN de um condomínio PENDENTE envia endereço, torres e áreas comuns; aqui as unidades
 * são geradas e tudo é gravado numa transação só. Se algo falha, nada fica e o condomínio continua
 * PENDENTE. Um segundo envio (duplo clique, reenvio após timeout) encontra CONCLUIDA e recebe 409.
 */
@Service
public class ConfiguracaoInicialService {

    // Limites provisórios, os mesmos do front (§5.1 do relatório do Setup Wizard); a equipe ainda vai
    // decidir os oficiais. Servem também para impedir que um envio gere milhões de linhas.
    static final int MAX_ANDARES = 100;
    static final int MAX_UNIDADES_POR_ANDAR = 100;
    static final int MAX_ANDAR_INICIAL = 999;
    static final int MAX_SEQUENCIA_INICIAL = 999;
    static final int MAX_DIGITOS = 4;
    static final int MAX_UNIDADES = 5000;
    static final int MAX_SEPARADOR = 3;

    private static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS",
            "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    @Autowired private CondominioRepository condominioRepository;
    @Autowired private TorreRepository torreRepository;
    @Autowired private ApartamentoRepository apartamentoRepository;
    @Autowired private AreaComumRepository areaComumRepository;

    @Transactional
    public ConfiguracaoInicialResponseDTO configurar(ConfiguracaoInicialDTO dados, UsuarioAutenticado auth) {
        if (!"ADMIN".equals(UsuarioService.normalizarPerfil(auth.getPerfil()))) {
            throw new SecurityException("Apenas o administrador do condomínio faz a configuração inicial.");
        }
        var condominio = condominioRepository.findParaConfiguracao(auth.getCondominioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Condomínio não encontrado."));
        if (condominio.isConfiguracaoConcluida()) {
            throw new ConflitoException("A configuração inicial deste condomínio já foi concluída.");
        }

        var modelo = dados.modelo() == null ? "VERTICAL" : dados.modelo().trim().toUpperCase();
        if (!"VERTICAL".equals(modelo)) {
            throw new IllegalArgumentException("Por enquanto só o modelo VERTICAL está disponível.");
        }
        condominio.setEndereco(obrigatorio(dados.endereco(), "Informe o endereço do condomínio.", 255));
        condominio.setCidade(obrigatorio(dados.cidade(), "Informe a cidade do condomínio.", 100));
        var estado = obrigatorio(dados.estado(), "Informe o estado (UF) do condomínio.", 2).toUpperCase();
        if (!UFS.contains(estado)) {
            throw new IllegalArgumentException("Estado inválido. Use a sigla da UF, como PE ou SP.");
        }
        condominio.setEstado(estado);

        var unidades = 0;
        var torres = gerarTorres(dados.torres(), condominio);
        for (var gerada : torres) {
            torreRepository.save(gerada.torre());
            apartamentoRepository.saveAll(gerada.unidades());
            unidades += gerada.unidades().size();
        }
        var areas = gerarAreas(dados.areasComuns(), condominio);
        areaComumRepository.saveAll(areas);

        condominio.setConfiguracaoInicial("CONCLUIDA");
        condominioRepository.save(condominio);
        return new ConfiguracaoInicialResponseDTO(condominio.getId(), "CONCLUIDA", torres.size(), unidades, areas.size());
    }

    // ---------------------------------------------------------------------

    // Lista, não Map: Torre compara pelo id, e torres ainda não gravadas seriam todas "iguais"
    private record TorreGerada(Torre torre, List<Apartamento> unidades) {}

    /** Valida todas as torres antes de gravar qualquer uma; a ordem do envio é mantida. */
    private List<TorreGerada> gerarTorres(List<ConfiguracaoInicialDTO.Torre> torres, Condominio condominio) {
        if (torres == null || torres.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma torre.");
        }
        var resultado = new ArrayList<TorreGerada>();
        var nomes = new HashSet<String>();
        var total = 0;
        for (var dados : torres) {
            var nome = obrigatorio(dados.nome(), "Informe o nome de cada torre.", 255);
            if (!nomes.add(nome.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("A torre \"" + nome + "\" aparece mais de uma vez.");
            }
            if (torreRepository.existsByCondominioIdAndNomeIgnoreCase(condominio.getId(), nome)) {
                throw new ConflitoException("Já existe uma torre \"" + nome + "\" neste condomínio.");
            }
            var andares = numero(dados.andares(), "andares", nome, MAX_ANDARES);
            var porAndar = numero(dados.unidadesPorAndar(), "unidades por andar", nome, MAX_UNIDADES_POR_ANDAR);
            var andarInicial = numero(dados.andarInicial(), "andar inicial", nome, MAX_ANDAR_INICIAL);
            var sequenciaInicial = numero(dados.sequenciaInicial(), "sequência inicial", nome, MAX_SEQUENCIA_INICIAL);
            var digitos = numero(dados.digitos(), "dígitos", nome, MAX_DIGITOS);
            var separador = dados.separador() == null ? "" : dados.separador();
            if (separador.length() > MAX_SEPARADOR) {
                throw new IllegalArgumentException("O separador da torre \"" + nome + "\" deve ter no máximo " + MAX_SEPARADOR + " caracteres.");
            }
            total += andares * porAndar;
            if (total > MAX_UNIDADES) {
                throw new IllegalArgumentException("A configuração passa de " + MAX_UNIDADES + " unidades.");
            }

            var torre = new Torre();
            torre.setNome(nome);
            torre.setCondominio(condominio);
            var unidades = new ArrayList<Apartamento>();
            var numeros = new HashSet<String>();
            for (int andar = andarInicial; andar < andarInicial + andares; andar++) {
                for (int sequencia = sequenciaInicial; sequencia < sequenciaInicial + porAndar; sequencia++) {
                    var numero = andar + separador + String.format("%0" + digitos + "d", sequencia);
                    // Ex.: sem separador e com 1 dígito, 1º andar unidade 11 e 11º andar unidade 1 viram "111"
                    if (!numeros.add(numero)) {
                        throw new IllegalArgumentException("A numeração da torre \"" + nome + "\" gera a unidade \"" + numero
                                + "\" mais de uma vez. Use um separador ou mais dígitos.");
                    }
                    var apartamento = new Apartamento();
                    apartamento.setNumero(numero);
                    apartamento.setStatus("LIVRE");
                    apartamento.setTorre(torre);
                    apartamento.setCondominio(condominio);
                    unidades.add(apartamento);
                }
            }
            resultado.add(new TorreGerada(torre, unidades));
        }
        return resultado;
    }

    private List<AreaComum> gerarAreas(List<ConfiguracaoInicialDTO.AreaComum> areas, Condominio condominio) {
        if (areas == null) return List.of();
        var resultado = new ArrayList<AreaComum>();
        var nomes = new HashSet<String>();
        for (var dados : areas) {
            var nome = obrigatorio(dados.nome(), "Informe o nome de cada área comum.", 255);
            if (!nomes.add(nome.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("A área comum \"" + nome + "\" aparece mais de uma vez.");
            }
            if (areaComumRepository.existsByCondominioIdAndNomeIgnoreCase(condominio.getId(), nome)) {
                throw new ConflitoException("Já existe uma área comum \"" + nome + "\" neste condomínio.");
            }
            var area = new AreaComum();
            area.setNome(nome);
            area.setDescricao(dados.descricao() == null || dados.descricao().isBlank() ? null : dados.descricao().trim());
            // Não há padrão "reservável": o admin escolhe (§10 do relatório do Setup Wizard)
            area.setReservavel(Boolean.TRUE.equals(dados.reservavel()));
            area.setExigeAprovacao(Boolean.TRUE.equals(dados.exigeAprovacao()));
            area.setCondominio(condominio);
            resultado.add(area);
        }
        return resultado;
    }

    private static String obrigatorio(String valor, String mensagem, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        var texto = valor.trim();
        if (texto.length() > tamanhoMaximo) {
            throw new IllegalArgumentException(mensagem.replace("Informe", "Revise") + " Máximo de " + tamanhoMaximo + " caracteres.");
        }
        return texto;
    }

    private static int numero(Integer valor, String campo, String torre, int maximo) {
        if (valor == null || valor < 1 || valor > maximo) {
            throw new IllegalArgumentException("Na torre \"" + torre + "\", " + campo + " deve ser um número entre 1 e " + maximo + ".");
        }
        return valor;
    }
}
