-- =============================================================================
-- Etapa 4, passo 1: comece de um banco limpo
--
-- O esquema atual foi criado a mao na Etapa 2. A partir de agora ele pertence ao
-- Flyway, e a V1 precisa encontrar um banco vazio.
--
-- ISSO E ACEITAVEL AQUI E EM NENHUM OUTRO LUGAR. Estamos descartando dados de
-- exercicio numa maquina local. Adotar Flyway num banco que ja tem dados de
-- verdade tem outro caminho: baseline-on-migrate=true, uma unica vez, marcando o
-- estado atual como linha de base para aplicar da V2 em diante.
-- =============================================================================

DROP TABLE IF EXISTS avaliacao CASCADE;
DROP TABLE IF EXISTS pagamento CASCADE;
DROP TABLE IF EXISTS item_pedido CASCADE;
DROP TABLE IF EXISTS pedido CASCADE;
DROP TABLE IF EXISTS item_cardapio CASCADE;
DROP TABLE IF EXISTS restaurante CASCADE;
DROP TABLE IF EXISTS categoria_restaurante CASCADE;
DROP TABLE IF EXISTS endereco_entrega CASCADE;
DROP TABLE IF EXISTS dono_restaurante CASCADE;
DROP TABLE IF EXISTS entregador CASCADE;
DROP TABLE IF EXISTS cliente CASCADE;
DROP TABLE IF EXISTS usuario CASCADE;
