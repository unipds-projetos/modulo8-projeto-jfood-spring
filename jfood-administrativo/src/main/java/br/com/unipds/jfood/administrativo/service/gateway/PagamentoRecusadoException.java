package br.com.unipds.jfood.administrativo.service.gateway;

public class PagamentoRecusadoException extends RuntimeException {

    public PagamentoRecusadoException(String motivo) {
        super(motivo);
    }
}
