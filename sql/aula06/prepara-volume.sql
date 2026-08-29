-- =============================================================================
-- Etapa 6: volume para medir paginacao, materialized view e valor_total
--
-- Com 22 pedidos nao ha o que medir: qualquer plano roda em microssegundos. Este
-- script cria 200 mil pedidos ENTREGUE para a cliente Ana Lima -- o historico de
-- quem usa o app ha anos, que e exatamente a tela que a Etapa 6 manda paginar --
-- e dois itens para cada um, para que a comparacao das tres implementacoes de
-- valor_total tenha de que somar.
--
-- Desfaz com limpa-volume.sql.
-- =============================================================================

INSERT INTO pedido (cliente_id, restaurante_id, endereco_entrega_id, entregador_id,
                    status, data_pedido, valor_total, taxa_entrega, distancia_km)
SELECT c.usuario_id,
       r.id,
       e.id,
       NULL,
       'ENTREGUE',
       -- espalha os pedidos ao longo dos ultimos ~5 anos, um a cada 13 minutos
       NOW() - (i * INTERVAL '13 minutes'),
       0,                                                   -- calculado no fim
       ROUND((5 + (i % 900) / 100.0)::NUMERIC, 2),
       ROUND((0.5 + (i % 1200) / 100.0)::NUMERIC, 2)
  FROM generate_series(1, 200000) AS i
 CROSS JOIN LATERAL (SELECT usuario_id FROM cliente c
                      JOIN usuario u ON u.id = c.usuario_id
                     WHERE u.nome = 'Ana Lima') AS c
 CROSS JOIN LATERAL (SELECT id FROM restaurante WHERE nome = 'Cantina do Zé') AS r
 CROSS JOIN LATERAL (SELECT id FROM endereco_entrega
                      WHERE cliente_id = c.usuario_id AND apelido = 'Casa') AS e;

-- Dois itens por pedido novo
INSERT INTO item_pedido (pedido_id, item_cardapio_id, quantidade, preco_unitario)
SELECT p.id, ic.id, 1 + (p.id % 3), ic.preco
  FROM pedido p
 CROSS JOIN LATERAL (
     SELECT id, preco FROM item_cardapio
      WHERE restaurante_id = p.restaurante_id
      ORDER BY id LIMIT 2
 ) AS ic
 WHERE p.id > 22;

-- E o valor_total mantido pela aplicacao -- aqui, pelo script de carga
UPDATE pedido p
   SET valor_total = p.taxa_entrega + (SELECT SUM(i.quantidade * i.preco_unitario)
                                         FROM item_pedido i WHERE i.pedido_id = p.id)
 WHERE p.id > 22;

-- Sem indice, o keyset nao tem como mostrar do que e capaz: a comparacao de
-- tupla (data_pedido, id) precisa de um indice na MESMA ordem para virar um
-- salto direto no B-tree em vez de uma varredura.
CREATE INDEX IF NOT EXISTS idx_pedido_historico_cliente
    ON pedido (cliente_id, data_pedido DESC, id DESC);

ANALYZE pedido;
ANALYZE item_pedido;
