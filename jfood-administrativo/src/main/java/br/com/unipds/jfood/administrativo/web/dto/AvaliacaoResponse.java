package br.com.unipds.jfood.administrativo.web.dto;

import java.time.OffsetDateTime;

public record AvaliacaoResponse(
        Long id,
        String cliente,
        Short nota,
        String comentario,
        OffsetDateTime criadoEm
) {}
