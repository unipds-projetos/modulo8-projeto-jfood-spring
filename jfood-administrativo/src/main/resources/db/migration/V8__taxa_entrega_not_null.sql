-- =============================================================================
-- V8 — passo 3 de 3: a taxa de entrega vira obrigatoria
--
-- Agora o SET NOT NULL passa, porque a V7 nao deixou nenhuma linha nula para
-- tras. Em uma tabela grande este comando faz uma varredura completa segurando
-- um lock de tabela -- e vale rodar em janela de baixo trafego.
-- =============================================================================

ALTER TABLE pedido
    ALTER COLUMN taxa_entrega SET NOT NULL;
