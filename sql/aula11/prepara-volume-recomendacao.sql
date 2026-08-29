-- =============================================================================
-- JFood — Etapa 11, item 1: volume para comparar SQL x Cypher
--
-- Cria 5.000 clientes, 100 restaurantes e ~200.000 avaliacoes nas tabelas REAIS
-- do JFood -- e nao numa tabela de benchmark -- para que a consulta medida seja
-- mesmo a consulta que a aplicacao faria.
--
-- Desfaz com limpa-volume-recomendacao.sql.
-- =============================================================================

-- 5.000 clientes
INSERT INTO usuario (id, nome, email, senha_hash, telefone)
SELECT 100000 + i, 'Cliente Bench ' || i, 'bench' || i || '@jfood.example',
       '$2a$10$benchbenchbenchbenchbenchbenchbenchbenchbenchbenchben', '11900000000'
  FROM generate_series(1, 5000) AS i;
INSERT INTO cliente (usuario_id, cpf)
SELECT 100000 + i, LPAD((90000000000 + i)::TEXT, 11, '0') FROM generate_series(1, 5000) AS i;
INSERT INTO endereco_entrega (cliente_id, apelido, cep, logradouro, numero, bairro, cidade, uf)
SELECT 100000 + i, 'Casa', '01311000', 'Avenida Paulista', i::TEXT, 'Bela Vista', 'São Paulo', 'SP'
  FROM generate_series(1, 5000) AS i;

-- 3.000 restaurantes: grafo ESPARSO (cerca de 67 avaliadores por restaurante).
-- Trocar este numero por 100 produz um grafo DENSO -- e a comparacao muda de lado.
-- Os dois cenarios estao medidos em docs/aula11.md.
INSERT INTO restaurante (id, nome, categoria_id, dono_id, cep)
SELECT 100000 + i,
       'Restaurante Bench ' || i,
       (SELECT id FROM categoria_restaurante ORDER BY id LIMIT 1 OFFSET (i % 5)),
       (SELECT usuario_id FROM dono_restaurante ORDER BY usuario_id LIMIT 1),
       '01311000'
  FROM generate_series(1, 3000) AS i;

-- ~200.000 pedidos ENTREGUE: cada cliente pede em 40 restaurantes
INSERT INTO pedido (id, cliente_id, restaurante_id, endereco_entrega_id, status,
                    data_pedido, valor_total, taxa_entrega, distancia_km)
SELECT 1000000 + (c - 1) * 40 + r,
       100000 + c,
       100000 + 1 + ((c * 7 + r * 13) % 3000),
       (SELECT id FROM endereco_entrega WHERE cliente_id = 100000 + c LIMIT 1),
       'ENTREGUE',
       NOW() - ((c + r) % 365 || ' days')::INTERVAL,
       50.00, 7.90, 3.00
  FROM generate_series(1, 5000) AS c
 CROSS JOIN generate_series(1, 40) AS r
ON CONFLICT DO NOTHING;

-- Uma avaliacao por pedido, com nota entre 1 e 5
INSERT INTO avaliacao (pedido_id, cliente_id, restaurante_id, nota, criado_em)
SELECT p.id, p.cliente_id, p.restaurante_id,
       1 + ((p.id * 31) % 5),
       p.data_pedido + INTERVAL '1 day'
  FROM pedido p
 WHERE p.id >= 1000000
ON CONFLICT DO NOTHING;

-- Os indices que qualquer DBA criaria para esta consulta -- a comparacao tem de
-- ser justa: o relacional entra no ringue com o que ele tem de melhor.
CREATE INDEX IF NOT EXISTS idx_avaliacao_cliente_nota ON avaliacao (cliente_id, nota);
CREATE INDEX IF NOT EXISTS idx_avaliacao_restaurante_nota ON avaliacao (restaurante_id, nota);

ANALYZE avaliacao;
ANALYZE pedido;
