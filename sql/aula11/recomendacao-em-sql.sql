-- =============================================================================
-- JFood — Etapa 11, item 1: a MESMA recomendacao, em SQL
--
-- "recomende restaurantes para a Ana com base no que outros clientes -- que
--  avaliaram bem os mesmos restaurantes que ela -- tambem avaliaram bem"
--
-- Sao TRES SALTOS. Cada salto vira um JOIN da tabela avaliacao com ela mesma:
--
--   salto 1: ana -> restaurantes que ela gostou       (avaliacao a1)
--   salto 2: esses restaurantes -> quem mais gostou   (avaliacao a2)
--   salto 3: esses clientes -> o que mais gostaram    (avaliacao a3)
--
-- Mais dois NOT EXISTS para excluir o que a Ana ja conhece, e mais um JOIN com
-- restaurante para trazer o nome. Total: 4 JOINs e 2 subconsultas correlacionadas.
--
-- Rodar isto para a Ana (cliente 10) e enganoso: ela tem 3 avaliacoes e uma
-- vizinhanca minuscula. O :cliente e passado por parametro para que a medicao
-- seja feita sobre um cliente do volume -- 40 avaliacoes, vizinhanca de milhares.
--
--   docker exec -i jfood-postgres psql -U postgres -d jfood-db \
--     -v cliente=100001 < sql/aula11/recomendacao-em-sql.sql
-- =============================================================================

EXPLAIN (ANALYZE, BUFFERS)
SELECT r.nome AS restaurante,
       COUNT(DISTINCT a2.cliente_id) AS forca_recomendacao
  FROM avaliacao a1
 INNER JOIN avaliacao a2 ON a2.restaurante_id = a1.restaurante_id
                        AND a2.cliente_id <> a1.cliente_id
                        AND a2.nota >= 4
 INNER JOIN avaliacao a3 ON a3.cliente_id = a2.cliente_id
                        AND a3.restaurante_id <> a1.restaurante_id
                        AND a3.nota >= 4
 INNER JOIN restaurante r ON r.id = a3.restaurante_id
 WHERE a1.cliente_id = :cliente
   AND a1.nota >= 4
   AND NOT EXISTS (SELECT 1 FROM avaliacao x
                    WHERE x.cliente_id = :cliente AND x.restaurante_id = a3.restaurante_id)
   AND NOT EXISTS (SELECT 1 FROM pedido p
                    WHERE p.cliente_id = :cliente AND p.restaurante_id = a3.restaurante_id)
 GROUP BY r.nome
 ORDER BY forca_recomendacao DESC
 LIMIT 5;
