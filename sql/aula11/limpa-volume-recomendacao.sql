-- Desfaz o prepara-volume-recomendacao.sql.
DELETE FROM avaliacao WHERE pedido_id >= 1000000;
DELETE FROM item_pedido WHERE pedido_id >= 1000000;
DELETE FROM pagamento WHERE pedido_id >= 1000000;
DELETE FROM pedido WHERE id >= 1000000;
DELETE FROM restaurante WHERE id >= 100000;
DELETE FROM endereco_entrega WHERE cliente_id >= 100000;
DELETE FROM cliente WHERE usuario_id >= 100000;
DELETE FROM usuario WHERE id >= 100000;
DROP INDEX IF EXISTS idx_avaliacao_cliente_nota;
DROP INDEX IF EXISTS idx_avaliacao_restaurante_nota;
SELECT setval('pedido_id_seq', (SELECT MAX(id) FROM pedido));
ANALYZE avaliacao;
