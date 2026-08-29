package br.com.unipds.jfood.catalogo.web;

import br.com.unipds.jfood.catalogo.service.RestauranteNaoEncontradoException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RestauranteNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNaoEncontrado(RestauranteNaoEncontradoException ex) {
        return Map.of("erro", ex.getMessage());
    }
}
