package br.com.condomais.condominio.dto;

import java.util.List;

/**
 * Envio do Setup Wizard. Traz os parâmetros de cada torre, não a lista de unidades: o backend gera as
 * unidades com a mesma regra da prévia do front. O condomínio vem do token; nome e CNPJ são do MASTER.
 */
public record ConfiguracaoInicialDTO(
        String modelo,      // VERTICAL, HORIZONTAL ou EDIFICIO; só a estrutura do modelo escolhido vem preenchida
        String endereco,
        String cidade,
        String estado,      // UF
        List<Torre> torres, // VERTICAL
        List<Rua> ruas,     // HORIZONTAL
        Edificio edificio,  // EDIFICIO
        List<AreaComum> areasComuns
) {
    /** Unidade gerada = andar + separador + sequência com zeros à esquerda até `digitos` (ex.: 1 + "" + 01 = "101"). */
    public record Torre(
            String nome,
            Integer andares,
            Integer unidadesPorAndar,
            Integer andarInicial,
            Integer sequenciaInicial,
            Integer digitos,
            String separador
    ) {}

    /** Casas numeradas em sequência, sem andar: numeroInicial 1 e 2 dígitos → "01", "02"… */
    public record Rua(
            String nome,
            Integer quantidadeCasas,
            Integer numeroInicial,
            Integer digitos
    ) {}

    /** Edifício pequeno: a mesma numeração por andar da torre, com as unidades direto no condomínio. */
    public record Edificio(
            Integer andares,
            Integer unidadesPorAndar,
            Integer andarInicial,
            Integer sequenciaInicial,
            Integer digitos,
            String separador
    ) {}

    public record AreaComum(
            String nome,
            String descricao,
            Boolean reservavel,
            Boolean exigeAprovacao,
            Integer capacidade  // opcional
    ) {}
}
