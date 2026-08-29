package br.com.unipds.jfood.recomendacoes.repository;

/**
 * A PROJECAO.
 *
 * A consulta nao precisa hidratar o grafo inteiro -- so quatro colunas. E a
 * diferenca entre uma resposta de algumas centenas de bytes e uma travessia
 * completa do objeto, com todas as avaliacoes de todos os clientes envolvidos.
 *
 * Os aliases do RETURN casam com os componentes do record: errar um deles quebra
 * o mapeamento.
 */
public record RecomendacaoRestaurante(
        String restauranteId,
        String nome,
        String categoria,
        long forcaRecomendacao,
        long pesoTotal
) {}
