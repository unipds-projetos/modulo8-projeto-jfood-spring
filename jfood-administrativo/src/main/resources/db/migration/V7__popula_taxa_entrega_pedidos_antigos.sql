-- =============================================================================
-- V7 — passo 2 de 3 da obrigatoriedade da taxa de entrega
--
-- Passo 1 (V2): a coluna nasceu nullable.
-- Passo 2 (aqui): as linhas antigas ganham valor.
-- Passo 3 (V8): a coluna vira NOT NULL.
--
-- Por que tres migracoes e nao uma: entre a V2 e a V8 a aplicacao continuou
-- rodando em producao. Se o NOT NULL viesse junto com o UPDATE, a janela entre
-- os dois comandos -- ainda que de milissegundos -- seria suficiente para um
-- INSERT sem taxa entrar e derrubar a migracao inteira.
--
-- O 0.00 e uma decisao de negocio assinada: para o pedido importado do sistema
-- legado, o JFood assume que nao houve cobranca de frete.
-- =============================================================================

UPDATE pedido
   SET taxa_entrega = 0.00
 WHERE taxa_entrega IS NULL;
