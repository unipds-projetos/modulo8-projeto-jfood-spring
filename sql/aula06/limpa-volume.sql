-- =============================================================================
-- Desfaz o prepara-volume.sql, devolvendo o banco ao estado do seed da V5.
-- =============================================================================

DELETE FROM item_pedido WHERE pedido_id > 22;
DELETE FROM pedido WHERE id > 22;
SELECT setval('pedido_id_seq', (SELECT MAX(id) FROM pedido));
DROP INDEX IF EXISTS idx_pedido_historico_cliente;
ANALYZE pedido;
