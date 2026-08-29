package br.com.unipds.jfood.catalogo.web.dto;

import java.math.BigDecimal;

/** Saida da aggregation pipeline do relatorio administrativo. */
public record RelatorioPorCategoria(
        String categoria,
        long totalItens,
        BigDecimal precoMedio,
        BigDecimal precoMinimo,
        BigDecimal precoMaximo,
        long restaurantesAbertos
) {}
