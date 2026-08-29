-- =============================================================================
-- Etapa 5, passo 3: enche a fila de despacho
--
-- Poe todos os pedidos como CONFIRMADO e nao despachados, para que duas rodadas
-- concorrentes do servico de despacho tenham do que disputar. Sao 22 pedidos e o
-- lote e de 20: a primeira instancia leva 20, a segunda leva os 2 restantes --
-- e nenhum pedido aparece nos dois lotes.
-- =============================================================================

UPDATE pedido SET status = 'CONFIRMADO', despachado_em = NULL;
