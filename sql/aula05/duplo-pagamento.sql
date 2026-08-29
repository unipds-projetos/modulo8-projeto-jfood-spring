-- =============================================================================
-- Etapa 5, passo 1: reproduza o duplo pagamento
--
-- Abra DUAS abas no DBeaver e execute os blocos INTERCALADOS, na ordem indicada.
-- Este e o bug que o resto da etapa corrige.
-- =============================================================================

-- ABA A -- (1)
BEGIN;
SELECT status FROM pedido WHERE id = 18;   -- le 'CRIADO': pode confirmar

-- ABA B -- (2)  roda ANTES de A commitar
BEGIN;
SELECT status FROM pedido WHERE id = 18;   -- le 'CRIADO' tambem! A ainda nao gravou

-- ABA A -- (3)
UPDATE pedido SET status = 'CONFIRMADO' WHERE id = 18;
INSERT INTO pagamento (pedido_id, metodo, valor, status, pago_em)
     VALUES (18, 'PIX', (SELECT valor_total FROM pedido WHERE id = 18), 'APROVADO', NOW());
COMMIT;

-- ABA B -- (4)
UPDATE pedido SET status = 'CONFIRMADO' WHERE id = 18;   -- passa sem erro
-- O INSERT abaixo so falha porque pagamento.pedido_id e UNIQUE. Tire o UNIQUE
-- mentalmente e voce tem DOIS pagamentos para o mesmo pedido.
COMMIT;

-- =============================================================================
-- O que aconteceu
--
-- Os dois leram 'CRIADO', os dois decidiram que podiam confirmar, os dois
-- gravaram. O READ COMMITTED do PostgreSQL fez o seu trabalho: nenhuma das duas
-- transacoes leu dado sujo. O problema nao e isolamento -- e a JANELA entre ler
-- o status e gravar a mudanca, onde o lost update nasce.
--
-- Fechar essa janela e o assunto da secao 5.3: adquirir o lock NA LEITURA.
-- =============================================================================

-- Para voltar ao estado inicial:
-- DELETE FROM pagamento WHERE pedido_id = 18;
-- UPDATE pedido SET status = 'CRIADO' WHERE id = 18;
