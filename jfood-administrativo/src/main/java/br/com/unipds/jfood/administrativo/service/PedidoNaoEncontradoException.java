package br.com.unipds.jfood.administrativo.service;

public class PedidoNaoEncontradoException extends RuntimeException {

    public PedidoNaoEncontradoException(Long pedidoId) {
        super("Pedido " + pedidoId + " nao encontrado");
    }
}
