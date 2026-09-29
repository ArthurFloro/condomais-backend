package br.com.condomais.core.exception;

/**
 * Operação que conflita com o estado atual dos dados: duplicidade, registros vinculados
 * impedindo exclusão, horário já reservado. Respondido como 409.
 * Estende IllegalArgumentException para manter compatíveis os tratamentos já existentes.
 */
public class ConflitoException extends IllegalArgumentException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
