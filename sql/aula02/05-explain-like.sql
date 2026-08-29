-- =============================================================================
-- JFood — Etapa 2, passo 6: a armadilha proposital do LIKE
--
-- Com 7 restaurantes o PostgreSQL varre a tabela nos dois casos -- e nao ha nada
-- para ver. A diferenca so aparece com volume e com indice, entao o script cria
-- os dois DENTRO de uma transacao e desfaz tudo no final: rodar este arquivo nao
-- deixa vestigio no banco.
-- =============================================================================

BEGIN;

-- 200 mil restaurantes sinteticos, reaproveitando categoria e dono existentes
INSERT INTO restaurante (nome, categoria_id, dono_id, cep, criado_em)
SELECT CASE WHEN i % 1000 = 0 THEN 'Pizzaria Numero ' || i
            WHEN i % 997  = 0 THEN 'Forno e Pizza ' || i
            ELSE 'Restaurante Sintetico ' || i
       END,
       (SELECT id FROM categoria_restaurante ORDER BY id LIMIT 1),
       (SELECT usuario_id FROM dono_restaurante ORDER BY usuario_id LIMIT 1),
       '01311000',
       NOW() - (i % 365 || ' days')::INTERVAL
  FROM generate_series(1, 200000) AS i;

-- varchar_pattern_ops e o detalhe que faz o indice servir para LIKE:
-- o indice B-tree padrao usa a colacao do banco (pt_BR, en_US...), cuja ordem
-- nao e a ordem de prefixo de caracteres. Sem esse operador, nem o 'Pizza%'
-- consegue usar o indice.
CREATE INDEX idx_restaurante_nome_pattern
    ON restaurante (nome varchar_pattern_ops);

ANALYZE restaurante;

\echo '=== 1) LIKE ''%pizza%'' -- curinga dos DOIS lados'
EXPLAIN ANALYZE
SELECT id, nome FROM restaurante WHERE nome LIKE '%Pizza%';

\echo '=== 2) LIKE ''Pizza%'' -- curinga so a direita'
EXPLAIN ANALYZE
SELECT id, nome FROM restaurante WHERE nome LIKE 'Pizza%';

ROLLBACK;
