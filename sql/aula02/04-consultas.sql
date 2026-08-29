-- =============================================================================
-- JFood — Etapa 2, passo 5: as consultas que o app precisa
--
-- Uma consulta por pergunta de negocio. Para cada uma, vale saber em que etapa
-- da ordem FROM -> WHERE -> GROUP BY -> HAVING -> SELECT -> ORDER BY cada
-- clausula atuou -- e o que esta anotado em cada bloco.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Ticket medio por restaurante, do maior para o menor
--
-- FROM junta as duas tabelas; GROUP BY colapsa por restaurante; AVG roda dentro
-- de cada grupo; ORDER BY ordena o resultado ja agregado.
-- Pedidos cancelados ficam de fora: eles nao sao receita, e deixa-los dentro
-- puxaria o ticket medio para baixo sem que ninguem entendesse por que.
-- -----------------------------------------------------------------------------

SELECT r.nome                      AS restaurante,
       COUNT(p.id)                 AS total_pedidos,
       ROUND(AVG(p.valor_total),2) AS ticket_medio
  FROM restaurante r
 INNER JOIN pedido p ON p.restaurante_id = r.id
 WHERE p.status <> 'CANCELADO'
 GROUP BY r.nome
 ORDER BY ticket_medio DESC;

-- -----------------------------------------------------------------------------
-- 2. Restaurantes com MAIS DE 3 pedidos nos ultimos 60 dias
--
-- Por que HAVING e nao WHERE: a contagem so existe DEPOIS do GROUP BY, e o WHERE
-- e avaliado ANTES dele -- linha a linha, quando nenhum grupo foi formado ainda.
-- "COUNT(*) > 3" no WHERE nem compila.
--
-- E por que o recorte de data esta no WHERE e nao no HAVING: ele filtra LINHAS
-- (cada pedido tem sua data), nao GRUPOS. Filtrar cedo tambem e mais barato --
-- o banco agrega menos linhas.
-- -----------------------------------------------------------------------------

SELECT r.nome        AS restaurante,
       COUNT(p.id)   AS pedidos_no_periodo
  FROM restaurante r
 INNER JOIN pedido p ON p.restaurante_id = r.id
 WHERE p.data_pedido >= NOW() - INTERVAL '60 days'
 GROUP BY r.nome
HAVING COUNT(p.id) > 3
 ORDER BY pedidos_no_periodo DESC;

-- -----------------------------------------------------------------------------
-- 3. Cliente, restaurante e valor de cada pedido (dois INNER JOIN encadeados)
--
-- O cliente e o restaurante estao a duas tabelas de distancia: pedido -> cliente
-- -> usuario. O nome nao mora em cliente; mora na tabela base da especializacao.
-- -----------------------------------------------------------------------------

SELECT p.id            AS pedido,
       u.nome          AS cliente,
       r.nome          AS restaurante,
       p.status,
       p.valor_total
  FROM pedido p
 INNER JOIN cliente c     ON c.usuario_id = p.cliente_id
 INNER JOIN usuario u     ON u.id = c.usuario_id
 INNER JOIN restaurante r ON r.id = p.restaurante_id
 ORDER BY p.data_pedido DESC;

-- -----------------------------------------------------------------------------
-- 4. TODOS os restaurantes com a contagem de pedidos ao lado, inclusive os que
--    nunca venderam -- o caso do plano "Duo" do Javify
--
-- Duas armadilhas moram aqui:
--   * INNER JOIN faria os restaurantes sem pedido sumirem da lista;
--   * COUNT(*) contaria a LINHA gerada pelo LEFT JOIN (com tudo nulo do lado
--     direito) e devolveria 1 para quem nunca vendeu. COUNT(p.id) ignora nulos
--     e devolve o 0 correto.
-- -----------------------------------------------------------------------------

SELECT r.nome                              AS restaurante,
       COUNT(p.id)                         AS total_pedidos,
       COALESCE(SUM(p.valor_total), 0)     AS receita
  FROM restaurante r
  LEFT JOIN pedido p ON p.restaurante_id = r.id
 GROUP BY r.nome
 ORDER BY total_pedidos DESC, restaurante;

-- -----------------------------------------------------------------------------
-- 5. Clientes cujo ticket medio esta ACIMA da media geral da plataforma
--
-- A subquery escalar do HAVING roda uma vez e devolve um numero unico -- a media
-- de todos os pedidos. Nao ha correlacao com a linha de fora, entao o PostgreSQL
-- a executa uma so vez para a consulta inteira.
-- -----------------------------------------------------------------------------

SELECT u.nome                        AS cliente,
       COUNT(p.id)                   AS pedidos,
       ROUND(AVG(p.valor_total), 2)  AS ticket_medio
  FROM pedido p
 INNER JOIN cliente c ON c.usuario_id = p.cliente_id
 INNER JOIN usuario u ON u.id = c.usuario_id
 GROUP BY u.nome
HAVING AVG(p.valor_total) > (SELECT AVG(valor_total) FROM pedido)
 ORDER BY ticket_medio DESC;

-- -----------------------------------------------------------------------------
-- 6. Restaurantes cadastrados ha mais de 30 dias que NUNCA receberam um pedido
--    (anti-join)
--
-- Tres formas de escrever a mesma pergunta. A que o time do JFood adota e a
-- primeira: NOT EXISTS le como a frase em portugues e nao tem a armadilha do
-- NOT IN, que devolve conjunto vazio se a subquery retornar um unico NULL.
-- -----------------------------------------------------------------------------

SELECT r.nome AS restaurante,
       r.criado_em::DATE AS cadastrado_em
  FROM restaurante r
 WHERE r.criado_em < NOW() - INTERVAL '30 days'
   AND NOT EXISTS (SELECT 1 FROM pedido p WHERE p.restaurante_id = r.id)
 ORDER BY r.criado_em;

-- A variante com LEFT JOIN ... IS NULL, que gera o mesmo plano no PostgreSQL:
SELECT r.nome AS restaurante,
       r.criado_em::DATE AS cadastrado_em
  FROM restaurante r
  LEFT JOIN pedido p ON p.restaurante_id = r.id
 WHERE p.id IS NULL
   AND r.criado_em < NOW() - INTERVAL '30 days'
 ORDER BY r.criado_em;
