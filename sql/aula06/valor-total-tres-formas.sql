-- =============================================================================
-- Etapa 6, item 7: as tres implementacoes de valor_total, medidas
--
-- (a) coluna materializada mantida pela APLICACAO  -- a escolha da Etapa 1
-- (b) coluna materializada mantida por TRIGGER
-- (c) calculo SOB DEMANDA em uma view
--
-- O script cria (b) e (c) dentro de uma transacao, mede as tres e faz ROLLBACK:
-- o banco fica exatamente como estava.
--
-- Rode depois do prepara-volume.sql.
-- =============================================================================

BEGIN;

\echo '### (c) a view sob demanda'
CREATE VIEW v_pedido_valor AS
SELECT p.id                                            AS pedido_id,
       p.taxa_entrega
       + COALESCE(SUM(i.quantidade * i.preco_unitario), 0) AS valor_total
  FROM pedido p
  LEFT JOIN item_pedido i ON i.pedido_id = p.id
 GROUP BY p.id, p.taxa_entrega;

\echo '### (b) o trigger que mantem a coluna'
CREATE OR REPLACE FUNCTION fn_recalcular_valor_total()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_pedido_id BIGINT := COALESCE(NEW.pedido_id, OLD.pedido_id);
BEGIN
    UPDATE pedido p
       SET valor_total = p.taxa_entrega + COALESCE((SELECT SUM(i.quantidade * i.preco_unitario)
                                                      FROM item_pedido i
                                                     WHERE i.pedido_id = v_pedido_id), 0)
     WHERE p.id = v_pedido_id;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_recalcular_valor_total
    AFTER INSERT OR UPDATE OR DELETE ON item_pedido
    FOR EACH ROW
    EXECUTE FUNCTION fn_recalcular_valor_total();

\echo ''
\echo '=========== LEITURA: uma pagina de 20 pedidos do historico ==========='
\echo '--- (a) e (b): a coluna (as duas leem igual, a diferenca esta na escrita)'
EXPLAIN ANALYZE
SELECT id, valor_total FROM pedido
 WHERE cliente_id = (SELECT usuario_id FROM cliente c JOIN usuario u ON u.id = c.usuario_id
                      WHERE u.nome = 'Ana Lima')
 ORDER BY data_pedido DESC, id DESC LIMIT 20;

\echo '--- (c) a view'
EXPLAIN ANALYZE
SELECT v.pedido_id, v.valor_total
  FROM v_pedido_valor v
  JOIN pedido p ON p.id = v.pedido_id
 WHERE p.cliente_id = (SELECT usuario_id FROM cliente c JOIN usuario u ON u.id = c.usuario_id
                        WHERE u.nome = 'Ana Lima')
 ORDER BY p.data_pedido DESC, p.id DESC LIMIT 20;

\echo ''
\echo '=========== ESCRITA: inserir 1.000 itens de pedido ==========='
\echo '--- (a) sem trigger: a aplicacao recalcula depois, em UMA passada'
ALTER TABLE item_pedido DISABLE TRIGGER trg_recalcular_valor_total;
EXPLAIN ANALYZE
INSERT INTO item_pedido (pedido_id, item_cardapio_id, quantidade, preco_unitario)
SELECT p.id, ic.id, 1, ic.preco
  FROM (SELECT id FROM pedido WHERE id > 22 ORDER BY id LIMIT 1000) p
 CROSS JOIN LATERAL (SELECT id, preco FROM item_cardapio
                      WHERE restaurante_id = (SELECT restaurante_id FROM pedido WHERE id = p.id)
                      ORDER BY id DESC LIMIT 1) ic;

\echo '--- (b) com trigger: cada linha inserida dispara um UPDATE em pedido'
ALTER TABLE item_pedido ENABLE TRIGGER trg_recalcular_valor_total;
EXPLAIN ANALYZE
INSERT INTO item_pedido (pedido_id, item_cardapio_id, quantidade, preco_unitario)
SELECT p.id, ic.id, 1, ic.preco
  FROM (SELECT id FROM pedido WHERE id > 22 ORDER BY id OFFSET 1000 LIMIT 1000) p
 CROSS JOIN LATERAL (SELECT id, preco FROM item_cardapio
                      WHERE restaurante_id = (SELECT restaurante_id FROM pedido WHERE id = p.id)
                      ORDER BY id DESC LIMIT 1) ic;

ROLLBACK;
