package br.com.unipds.jfood.administrativo.service;

import br.com.unipds.jfood.administrativo.domain.StatusPedido;

public class StatusPedidoInvalidoException extends RuntimeException {

    public StatusPedidoInvalidoException(Long pedidoId, StatusPedido atual, String operacao) {
        super("Pedido " + pedidoId + " esta " + atual + " e nao pode ser " + operacao);
    }
}
