package br.com.unipds.jfood.recomendacoes.repository;

/** Saida da consulta de deteccao de fraude. */
public record ContaVinculada(
        String contaA,
        String contaB,
        String tipoDeVinculo,
        String vinculo
) {}
