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
 * Setup Wizard: o ADMIN de um condomínio PENDENTE envia endereço, a estrutura e as áreas comuns; aqui as
 * unidades são geradas e tudo é gravado numa transação só. Se algo falha, nada fica e o condomínio continua
 * PENDENTE. Um segundo envio (duplo clique, reenvio após timeout) encontra CONCLUIDA e recebe 409.
 *
 * Os três modelos usam as mesmas tabelas: VERTICAL = torres com unidades por andar; HORIZONTAL = ruas
 * (gravadas como torres) com casas numeradas em sequência; EDIFICIO = unidades por andar sem torre.
 */
@Service
public class ConfiguracaoInicialService {

    // Limites provisórios, os mesmos do front (§5.1 do relatório do Setup Wizard); a equipe ainda vai
    // decidir os oficiais. Servem também para impedir que um envio gere milhões de linhas.
    static final int MAX_ANDARES = 100;
    static final int MAX_UNIDADES_POR_ANDAR = 100;
    static final int MAX_ANDAR_INICIAL = 999;
    static final int MAX_SEQUENCIA_INICIAL = 999;
    static final int MAX_CASAS_POR_RUA = 999;
    static final int MAX_DIGITOS = 4;
    static final int MAX_UNIDADES = 5000;
    static final int MAX_SEPARADOR = 3;

    private static final Set<String> MODELOS = Set.of("VERTICAL", "HORIZONTAL", "EDIFICIO");
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
        if (!MODELOS.contains(modelo)) {
            throw new IllegalArgumentException("Modelo inválido. Use VERTICAL, HORIZONTAL ou EDIFICIO.");
        }
        condominio.setModelo(modelo);
        condominio.setEndereco(obrigatorio(dados.endereco(), "Informe o endereço do condomínio.", 255));
        condominio.setCidade(obrigatorio(dados.cidade(), "Informe a cidade do condomínio.", 100));
        var estado = obrigatorio(dados.estado(), "Informe o estado (UF) do condomínio.", 2).toUpperCase();
        if (!UFS.contains(estado)) {
            throw new IllegalArgumentException("Estado inválido. Use a sigla da UF, como PE ou SP.");
        }
        condominio.setEstado(estado);

        // Toda a estrutura é montada e validada antes do primeiro save
        var estrutura = switch (modelo) {
            case "VERTICAL" -> {
                exigirVazio(dados.ruas() == null || dados.ruas().isEmpty(), "ruas", modelo);
                exigirVazio(dados.edificio() == null, "edificio", modelo);
                yield gerarTorres(dados.torres(), condominio);
            }
            case "HORIZONTAL" -> {
                exigirVazio(dados.torres() == null || dados.torres().isEmpty(), "torres", modelo);
                exigirVazio(dados.edificio() == null, "edificio", modelo);
                yield gerarRuas(dados.ruas(), condominio);
            }
            default -> {
                exigirVazio(dados.torres() == null || dados.torres().isEmpty(), "torres", modelo);
                exigirVazio(dados.ruas() == null || dados.ruas().isEmpty(), "ruas", modelo);
                yield gerarEdificio(dados.edificio(), condominio);
            }
        };
        var areas = gerarAreas(dados.areasComuns(), condominio);

        var unidades = 0;
        var agrupadores = 0;
        for (var grupo : estrutura) {
            if (grupo.torre() != null) {
                torreRepository.save(grupo.torre());
                agrupadores++;
            }
            apartamentoRepository.saveAll(grupo.unidades());
            unidades += grupo.unidades().size();
        }
        areaComumRepository.saveAll(areas);

        condominio.setConfiguracaoInicial("CONCLUIDA");
        condominioRepository.save(condominio);
        return new ConfiguracaoInicialResponseDTO(condominio.getId(), "CONCLUIDA", modelo, agrupadores, unidades, areas.size());
    }

    // ---------------------------------------------------------------------
    // Geração por modelo
    // ---------------------------------------------------------------------

    // Torre (ou rua) e as unidades dela; torre nula = unidades direto no condomínio (EDIFICIO).
    // Lista, não Map: Torre compara pelo id, e torres ainda não gravadas seriam todas "iguais".
    private record Grupo(Torre torre, List<Apartamento> unidades) {}

    /** VERTICAL: cada torre gera andar + separador + sequência (1, "", 01 → "101"). */
    private List<Grupo> gerarTorres(List<ConfiguracaoInicialDTO.Torre> torres, Condominio condominio) {
        if (torres == null || torres.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma torre.");
        }
        var resultado = new ArrayList<Grupo>();
        var nomes = new HashSet<String>();
        var contador = new int[1];
        for (var dados : torres) {
            var nome = nomeDoAgrupador(dados.nome(), "torre", nomes, condominio);
            var torre = novaTorre(nome, condominio);
            var unidades = gerarPorAndar(dados.andares(), dados.unidadesPorAndar(), dados.andarInicial(), dados.sequenciaInicial(),
                    dados.digitos(), dados.separador(), "na torre \"" + nome + "\"", torre, condominio, contador);
            resultado.add(new Grupo(torre, unidades));
        }
        return resultado;
    }

    /** HORIZONTAL: cada rua gera casas numeradas em sequência, sem andar (01, 02, 03…). */
    private List<Grupo> gerarRuas(List<ConfiguracaoInicialDTO.Rua> ruas, Condominio condominio) {
        if (ruas == null || ruas.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma rua.");
        }
        var resultado = new ArrayList<Grupo>();
        var nomes = new HashSet<String>();
        var total = 0;
        for (var dados : ruas) {
            var nome = nomeDoAgrupador(dados.nome(), "rua", nomes, condominio);
            var onde = "na rua \"" + nome + "\"";
            var casas = numero(dados.quantidadeCasas(), "quantidade de casas", onde, MAX_CASAS_POR_RUA);
            var numeroInicial = numero(dados.numeroInicial(), "número inicial", onde, MAX_SEQUENCIA_INICIAL);
            var digitos = numero(dados.digitos(), "dígitos", onde, MAX_DIGITOS);
            total += casas;
            limiteTotal(total);

            var rua = novaTorre(nome, condominio);
            var unidades = new ArrayList<Apartamento>();
            for (int sequencia = numeroInicial; sequencia < numeroInicial + casas; sequencia++) {
                unidades.add(novaUnidade(String.format("%0" + digitos + "d", sequencia), null, rua, condominio));
            }
            resultado.add(new Grupo(rua, unidades));
        }
        return resultado;
    }

    /** EDIFICIO: mesma numeração por andar da torre, mas as unidades ficam direto no condomínio. */
    private List<Grupo> gerarEdificio(ConfiguracaoInicialDTO.Edificio dados, Condominio condominio) {
        if (dados == null) {
            throw new IllegalArgumentException("Informe a estrutura do edifício.");
        }
        var unidades = gerarPorAndar(dados.andares(), dados.unidadesPorAndar(), dados.andarInicial(), dados.sequenciaInicial(),
                dados.digitos(), dados.separador(), "no edifício", null, condominio, new int[1]);
        for (var unidade : unidades) {
            if (apartamentoRepository.existsByCondominioIdAndTorreIsNullAndNumero(condominio.getId(), unidade.getNumero())) {
                throw new ConflitoException("Já existe uma unidade \"" + unidade.getNumero() + "\" neste condomínio.");
            }
        }
        return List.of(new Grupo(null, unidades));
    }

    private List<Apartamento> gerarPorAndar(Integer andaresInformados, Integer porAndarInformado, Integer andarInicialInformado,
                                            Integer sequenciaInicialInformada, Integer digitosInformados, String separadorInformado,
                                            String onde, Torre torre, Condominio condominio, int[] contador) {
        var andares = numero(andaresInformados, "andares", onde, MAX_ANDARES);
        var porAndar = numero(porAndarInformado, "unidades por andar", onde, MAX_UNIDADES_POR_ANDAR);
        var andarInicial = numero(andarInicialInformado, "andar inicial", onde, MAX_ANDAR_INICIAL);
        var sequenciaInicial = numero(sequenciaInicialInformada, "sequência inicial", onde, MAX_SEQUENCIA_INICIAL);
        var digitos = numero(digitosInformados, "dígitos", onde, MAX_DIGITOS);
        var separador = separadorInformado == null ? "" : separadorInformado;
        if (separador.length() > MAX_SEPARADOR) {
            throw new IllegalArgumentException("O separador " + onde + " deve ter no máximo " + MAX_SEPARADOR + " caracteres.");
        }
        contador[0] += andares * porAndar;
        limiteTotal(contador[0]);

        var unidades = new ArrayList<Apartamento>();
        var numeros = new HashSet<String>();
        for (int andar = andarInicial; andar < andarInicial + andares; andar++) {
            for (int sequencia = sequenciaInicial; sequencia < sequenciaInicial + porAndar; sequencia++) {
                var numero = andar + separador + String.format("%0" + digitos + "d", sequencia);
                // Ex.: sem separador e com 1 dígito, 1º andar unidade 11 e 11º andar unidade 1 viram "111"
                if (!numeros.add(numero)) {
                    throw new IllegalArgumentException("A numeração " + onde + " gera a unidade \"" + numero
                            + "\" mais de uma vez. Use um separador ou mais dígitos.");
                }
                unidades.add(novaUnidade(numero, andar, torre, condominio));
            }
        }
        return unidades;
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
            area.setCapacidade(CondominioService.validarCapacidade(dados.capacidade()));
            area.setCondominio(condominio);
            resultado.add(area);
        }
        return resultado;
    }

    // ---------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------

    private String nomeDoAgrupador(String informado, String tipo, Set<String> nomes, Condominio condominio) {
        var nome = obrigatorio(informado, "Informe o nome de cada " + tipo + ".", 255);
        if (!nomes.add(nome.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("A " + tipo + " \"" + nome + "\" aparece mais de uma vez.");
        }
        if (torreRepository.existsByCondominioIdAndNomeIgnoreCase(condominio.getId(), nome)) {
            throw new ConflitoException("Já existe uma " + tipo + " \"" + nome + "\" neste condomínio.");
        }
        return nome;
    }

    private static Torre novaTorre(String nome, Condominio condominio) {
        var torre = new Torre();
        torre.setNome(nome);
        torre.setCondominio(condominio);
        return torre;
    }

    private static Apartamento novaUnidade(String numero, Integer andar, Torre torre, Condominio condominio) {
        var apartamento = new Apartamento();
        apartamento.setNumero(numero);
        apartamento.setAndar(andar);
        apartamento.setStatus("LIVRE");
        apartamento.setTorre(torre);
        apartamento.setCondominio(condominio);
        return apartamento;
    }

    private static void limiteTotal(int total) {
        if (total > MAX_UNIDADES) {
            throw new IllegalArgumentException("A configuração passa de " + MAX_UNIDADES + " unidades.");
        }
    }

    // O envio traz só a estrutura do modelo escolhido; campos de outro modelo indicam um erro no front
    private static void exigirVazio(boolean vazio, String campo, String modelo) {
        if (!vazio) {
            throw new IllegalArgumentException("O campo \"" + campo + "\" não se aplica ao modelo " + modelo + ".");
        }
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

    private static int numero(Integer valor, String campo, String onde, int maximo) {
        if (valor == null || valor < 1 || valor > maximo) {
            throw new IllegalArgumentException(Character.toUpperCase(onde.charAt(0)) + onde.substring(1) + ", " + campo + " deve ser um número entre 1 e " + maximo + ".");
        }
        return valor;
    }
}
