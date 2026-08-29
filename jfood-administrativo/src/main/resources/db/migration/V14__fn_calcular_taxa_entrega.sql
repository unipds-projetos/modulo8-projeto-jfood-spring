-- =============================================================================
-- V14 — a funcao de taxa de entrega
--
-- Por que esta regra pertence ao banco: ela e lida por mais de um consumidor --
-- a API, o painel de operacoes e o relatorio financeiro -- e todos precisam da
-- MESMA faixa progressiva. Reimplementar em tres lugares e como garantir que os
-- tres vao divergir.
--
-- O que NAO pertence ao banco: regra que muda toda semana, ou que precisa de
-- teste unitario rapido. PL/pgSQL nao tem o ferramental de teste que o Java tem.
-- =============================================================================

CREATE OR REPLACE FUNCTION calcular_taxa_entrega(p_pedido_id BIGINT)
RETURNS NUMERIC(10,2)
LANGUAGE plpgsql
AS $$
DECLARE
    v_distancia NUMERIC(6,2);
    v_status    VARCHAR(20);
    v_taxa      NUMERIC(10,2);
BEGIN
    SELECT distancia_km, status
      INTO v_distancia, v_status
      FROM pedido
     WHERE id = p_pedido_id;

    -- NOT FOUND e a forma correta de detectar que o SELECT nao trouxe linha.
    -- ERRCODE nomeado deixa a aplicacao distinguir os casos sem parsear texto.
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Pedido % nao encontrado', p_pedido_id
            USING ERRCODE = 'P0002';
    END IF;

    IF v_status = 'ENTREGUE' THEN
        RAISE EXCEPTION 'Pedido % ja foi entregue; a taxa nao pode mais ser recalculada', p_pedido_id
            USING ERRCODE = 'P0001';
    END IF;

    -- Faixa progressiva: cada quilometro custa o preco da SUA faixa, e nao o da
    -- ultima -- e a mesma logica de imposto de renda.
    v_taxa := 5.90                                            -- taxa base, ate 2 km
            + GREATEST(LEAST(v_distancia,  6) - 2, 0) * 1.50  -- de 2 a 6 km
            + GREATEST(LEAST(v_distancia, 12) - 6, 0) * 1.00  -- de 6 a 12 km
            + GREATEST(v_distancia - 12, 0) * 0.70;           -- acima de 12 km

    RETURN ROUND(v_taxa, 2);
END;
$$;
