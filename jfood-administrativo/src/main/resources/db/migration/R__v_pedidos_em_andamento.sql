-- =============================================================================
-- R__ — a view de operacao
--
-- Repetivel porque view nao guarda dado: reaplica-la e so reescrever a
-- definicao, e CREATE OR REPLACE torna a operacao idempotente. Quando o time
-- acrescentar uma coluna a tela do painel, edita este arquivo e sobe -- sem
-- inflar a linha do tempo com V18, V19, V20 do mesmo objeto.
--
-- (A materialized view da V17 e o caso oposto, e o porque esta la.)
-- =============================================================================

CREATE OR REPLACE VIEW v_pedidos_em_andamento AS
SELECT p.id                AS pedido_id,
       p.status,
       p.data_pedido,
       p.valor_total,
       p.taxa_entrega,
       p.distancia_km,
       uc.nome             AS cliente,
       r.nome              AS restaurante,
       cat.nome            AS categoria,
       ue.nome             AS entregador,
       e.tipo_veiculo      AS veiculo,
       ee.bairro           AS bairro_entrega,
       ee.cidade           AS cidade_entrega
  FROM pedido p
 INNER JOIN cliente c              ON c.usuario_id = p.cliente_id
 INNER JOIN usuario uc             ON uc.id = c.usuario_id
 INNER JOIN restaurante r          ON r.id = p.restaurante_id
 INNER JOIN categoria_restaurante cat ON cat.id = r.categoria_id
 INNER JOIN endereco_entrega ee    ON ee.id = p.endereco_entrega_id
  LEFT JOIN entregador e           ON e.usuario_id = p.entregador_id
  LEFT JOIN usuario ue             ON ue.id = e.usuario_id
 WHERE p.status IN ('CRIADO', 'CONFIRMADO', 'EM_PREPARO', 'A_CAMINHO');
