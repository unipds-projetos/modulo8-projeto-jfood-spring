package br.com.unipds.jfood.catalogo.service;

public class RestauranteNaoEncontradoException extends RuntimeException {

    public RestauranteNaoEncontradoException(String id) {
        super("Restaurante " + id + " nao encontrado");
    }
}
