package br.com.condomais.core.exception;

/**
 * Registro inexistente (ou fora do condomínio do usuário). Respondido como 404.
 * Estende IllegalArgumentException para manter compatíveis os tratamentos já existentes.
 */
public class RecursoNaoEncontradoException extends IllegalArgumentException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
