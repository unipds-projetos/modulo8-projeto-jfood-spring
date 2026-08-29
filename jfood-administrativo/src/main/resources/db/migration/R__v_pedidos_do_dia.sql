-- =============================================================================
-- R__ — o painel operacional
--
-- Migracao REPETIVEL: sem numero de versao, reaplicada sempre que o checksum do
-- arquivo muda -- ou seja, sempre que voce edita a view. E executada DEPOIS de
-- todas as versionadas, entao pode referenciar qualquer tabela com seguranca.
--
-- CREATE OR REPLACE e obrigatorio: na segunda execucao, um CREATE VIEW puro
-- falharia porque o objeto ja existe. O principio e a idempotencia -- rodar duas
-- vezes tem de produzir o mesmo resultado que rodar uma.
-- =============================================================================

CREATE OR REPLACE VIEW v_pedidos_do_dia AS
SELECT p.id                AS pedido_id,
       p.status,
       p.data_pedido,
       p.valor_total,
       p.taxa_entrega,
       uc.nome             AS cliente,
       r.nome              AS restaurante,
       ue.nome             AS entregador
  FROM pedido p
 INNER JOIN cliente c      ON c.usuario_id = p.cliente_id
 INNER JOIN usuario uc     ON uc.id = c.usuario_id
 INNER JOIN restaurante r  ON r.id = p.restaurante_id
  LEFT JOIN entregador e   ON e.usuario_id = p.entregador_id
  LEFT JOIN usuario ue     ON ue.id = e.usuario_id
 WHERE p.data_pedido >= CURRENT_DATE;
