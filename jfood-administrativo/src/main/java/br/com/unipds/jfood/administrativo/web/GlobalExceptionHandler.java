package br.com.unipds.jfood.administrativo.web;

import br.com.unipds.jfood.administrativo.service.PedidoNaoEncontradoException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PedidoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNaoEncontrado(PedidoNaoEncontradoException ex) {
        return Map.of("erro", ex.getMessage());
    }
}
