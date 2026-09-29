package br.com.condomais.core.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Converte exceções lançadas pelos controllers/services em respostas HTTP com corpo padronizado.
 * Sem este handler, qualquer exceção era encaminhada para /error (rota protegida) e chegava ao
 * cliente como 403, indistinguível de falta de permissão.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponseDTO> naoEncontrado(RecursoNaoEncontradoException e, HttpServletRequest request) {
        return responder(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    // orElseThrow() sem mensagem
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErroResponseDTO> naoEncontradoSemMensagem(NoSuchElementException e, HttpServletRequest request) {
        return responder(HttpStatus.NOT_FOUND, "Registro não encontrado.", request);
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResponseDTO> conflito(ConflitoException e, HttpServletRequest request) {
        return responder(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    // Regras de negócio violadas (demais IllegalArgumentException dos services)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResponseDTO> requisicaoInvalida(IllegalArgumentException e, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    // JSON malformado ou com tipo errado no corpo (ex.: UUID inválido)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponseDTO> corpoInvalido(HttpMessageNotReadableException e, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou mal formatado.", request);
    }

    // Parâmetro de rota/query com tipo errado (ex.: /apartamentos/abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponseDTO> parametroInvalido(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "Valor inválido para o parâmetro '" + e.getName() + "'.", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErroResponseDTO> parametroAusente(MissingServletRequestParameterException e, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: '" + e.getParameterName() + "'.", request);
    }

    // Falha de login (CPF inexistente, senha errada, usuário sem senha)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResponseDTO> naoAutenticado(AuthenticationException e, HttpServletRequest request) {
        return responder(HttpStatus.UNAUTHORIZED, "CPF ou senha inválidos.", request);
    }

    // Acesso a dados de outro condomínio
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErroResponseDTO> acessoNegado(SecurityException e, HttpServletRequest request) {
        return responder(HttpStatus.FORBIDDEN, e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponseDTO> erroInesperado(Exception e, HttpServletRequest request) {
        // Exceções do próprio Spring MVC (405, 404 de rota inexistente, 415...) já trazem o status correto
        if (e instanceof ErrorResponse erroSpring) {
            var status = HttpStatus.valueOf(erroSpring.getStatusCode().value());
            return responder(status, erroSpring.getBody().getDetail(), request);
        }
        log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao processar a requisição.", request);
    }

    private ResponseEntity<ErroResponseDTO> responder(HttpStatus status, String mensagem, HttpServletRequest request) {
        var corpo = new ErroResponseDTO(status.value(), status.getReasonPhrase(), mensagem, request.getRequestURI(), Instant.now());
        return ResponseEntity.status(status).body(corpo);
    }
}
