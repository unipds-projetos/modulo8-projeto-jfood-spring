package br.com.unipds.jfood.administrativo.service;

public class CupomIndisponivelException extends RuntimeException {

    public CupomIndisponivelException(String motivo) {
        super(motivo);
    }
}
