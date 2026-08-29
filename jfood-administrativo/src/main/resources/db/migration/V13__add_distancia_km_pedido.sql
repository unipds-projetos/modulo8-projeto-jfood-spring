-- =============================================================================
-- V13 — a distancia da entrega
--
-- A funcao de taxa de entrega da Etapa 6 precisa de um insumo que o esquema
-- ainda nao tinha. A coluna nasce nullable (mesmo raciocinio da V2) e ganha
-- valor para os pedidos existentes logo abaixo.
-- =============================================================================

ALTER TABLE pedido
    ADD COLUMN distancia_km NUMERIC(6,2);

ALTER TABLE pedido
    ADD CONSTRAINT ck_pedido_distancia_nao_negativa CHECK (distancia_km >= 0);

-- Distancia sintetica e DETERMINISTICA para os pedidos que ja existem: entre
-- 0,5 km e 12,4 km, derivada do proprio id. Nada de random() -- uma migracao
-- precisa produzir o mesmo banco toda vez que roda.
UPDATE pedido
   SET distancia_km = ROUND(0.5 + (id * 7 % 120) / 10.0, 2)
 WHERE distancia_km IS NULL;
