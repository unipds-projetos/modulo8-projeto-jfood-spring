-- =============================================================================
-- V16 — trigger BEFORE de validacao
--
-- Regra: um entregador nao pode ter mais de uma entrega EM ANDAMENTO.
--
-- A clausula WHEN e o que impede a validacao de custar em toda escrita: sem ela,
-- todo UPDATE em pedido -- inclusive o recalculo de valor_total -- pagaria uma
-- consulta a mais.
-- =============================================================================

CREATE OR REPLACE FUNCTION fn_validar_entrega_unica()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_em_andamento INT;
BEGIN
    SELECT COUNT(*)
      INTO v_em_andamento
      FROM pedido
     WHERE entregador_id = NEW.entregador_id
       AND status = 'A_CAMINHO'
       AND id <> NEW.id;

    IF v_em_andamento > 0 THEN
        RAISE EXCEPTION 'Entregador % ja tem uma entrega em andamento', NEW.entregador_id
            USING ERRCODE = 'P0003';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validar_entrega_unica
    BEFORE INSERT OR UPDATE ON pedido
    FOR EACH ROW
    WHEN (NEW.status = 'A_CAMINHO' AND NEW.entregador_id IS NOT NULL)
    EXECUTE FUNCTION fn_validar_entrega_unica();
