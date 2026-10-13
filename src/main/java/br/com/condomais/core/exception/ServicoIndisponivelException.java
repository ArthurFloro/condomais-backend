package br.com.condomais.core.exception;

/** Dependência externa desligada ou fora do ar (ex.: serviço do assistente virtual não configurado ou fora do ar). Vira 503. */
public class ServicoIndisponivelException extends RuntimeException {
    public ServicoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
