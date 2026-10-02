package br.com.condomais.core.exception;

/** Dependência externa desligada ou fora do ar (ex.: assistente sem chave da Claude API). Vira 503. */
public class ServicoIndisponivelException extends RuntimeException {
    public ServicoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
