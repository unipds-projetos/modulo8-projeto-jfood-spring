-- =============================================================================
-- V17 — a materialized view de faturamento
--
-- Por que ESTA vai de V__ e a view da operacao vai de R__:
--
--   A view nao guarda dado. Reaplica-la e reescrever uma definicao -- barato,
--   instantaneo, e ela volta atualizada. R__ e o lugar certo.
--
--   A materialized view GUARDA dado. Um DROP + CREATE dela reexecuta o JOIN
--   inteiro e derruba os indices junto. Fazer isso a cada edicao do arquivo, em
--   toda subida de toda instancia, e caro e imprevisivel -- e o REFRESH, que e a
--   operacao que ela realmente precisa, e trabalho de job agendado, nao de
--   migracao.
-- =============================================================================

CREATE MATERIALIZED VIEW mv_faturamento_por_restaurante AS
SELECT r.id                            AS restaurante_id,
       r.nome                          AS restaurante,
       c.nome                          AS categoria,
       COUNT(p.id)                     AS pedidos_entregues,
       COALESCE(SUM(p.valor_total), 0) AS receita_total,
       ROUND(COALESCE(AVG(p.valor_total), 0), 2) AS ticket_medio,
       NOW()                           AS atualizado_em
  FROM restaurante r
 INNER JOIN categoria_restaurante c ON c.id = r.categoria_id
  LEFT JOIN pedido p ON p.restaurante_id = r.id AND p.status = 'ENTREGUE'
 GROUP BY r.id, r.nome, c.nome
WITH DATA;

-- Obrigatorio para o REFRESH ... CONCURRENTLY. Sem CONCURRENTLY, o REFRESH
-- adquire lock exclusivo e BLOQUEIA TODAS AS LEITURAS enquanto reconstroi --
-- justamente o que a materialized view existia para evitar.
CREATE UNIQUE INDEX idx_mv_faturamento_restaurante
    ON mv_faturamento_por_restaurante (restaurante_id);
