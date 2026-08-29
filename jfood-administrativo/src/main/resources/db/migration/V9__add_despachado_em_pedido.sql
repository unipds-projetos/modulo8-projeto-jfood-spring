-- =============================================================================
-- V9 — a fila de despacho
--
-- O servico de despacho roda em varias instancias. Cada uma precisa pegar seu
-- lote de pedidos confirmados sem colidir com as outras -- e a coluna
-- despachado_em e o que marca quem ja foi processado.
-- =============================================================================

ALTER TABLE pedido
    ADD COLUMN despachado_em TIMESTAMPTZ;

-- Indice PARCIAL: o WHERE na definicao faz o indice cobrir so as linhas que o
-- job consulta. Numa base de demonstracao a diferenca nao aparece; em producao,
-- com milhoes de pedidos ENTREGUE e algumas centenas CONFIRMADO, a diferenca e
-- entre um indice de kilobytes e um de gigabytes.
CREATE INDEX idx_pedido_fila_despacho
    ON pedido (data_pedido)
 WHERE status = 'CONFIRMADO' AND despachado_em IS NULL;
