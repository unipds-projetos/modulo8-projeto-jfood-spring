package br.com.unipds.jfood.tracking.web.dto;

import java.time.Instant;

/** O que o app do entregador envia a cada 5 segundos. */
public record PingRequest(
        Long entregadorId,
        Long entregaId,
        Double latitude,
        Double longitude,
        Double velocidadeKmh,
        Instant instante
) {

    public Instant instanteOuAgora() {
        return instante == null ? Instant.now() : instante;
    }
}
