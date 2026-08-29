package br.com.unipds.jfood.administrativo.service;

public class TaxaNaoRecalculavelException extends RuntimeException {

    public TaxaNaoRecalculavelException(String mensagem) {
        super(mensagem);
    }
}
