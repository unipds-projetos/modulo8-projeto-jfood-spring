-- =============================================================================
-- V15 — auditoria de mudanca de status por trigger
--
-- Por que trigger e nao codigo de aplicacao: o UPDATE feito direto no DBeaver as
-- duas da manha tambem precisa ser registrado. Auditoria na aplicacao audita a
-- aplicacao; auditoria no banco audita o DADO.
-- =============================================================================

CREATE TABLE auditoria_pedido (
    id             BIGSERIAL    PRIMARY KEY,
    pedido_id      BIGINT       NOT NULL,
    status_antigo  VARCHAR(20),
    status_novo    VARCHAR(20)  NOT NULL,
    alterado_por   TEXT         NOT NULL,
    alterado_em    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_auditoria_pedido_pedido ON auditoria_pedido (pedido_id, alterado_em DESC);

-- A tabela e IMUTAVEL: so INSERT. Sem UPDATE e sem DELETE, nem pela aplicacao.
-- Uma trilha que pode ser editada nao serve como trilha.
REVOKE UPDATE, DELETE ON auditoria_pedido FROM PUBLIC;

CREATE OR REPLACE FUNCTION fn_auditar_status_pedido()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO auditoria_pedido (pedido_id, status_antigo, status_novo, alterado_por, alterado_em)
         VALUES (NEW.id, OLD.status, NEW.status, current_user, NOW());
    -- Em trigger AFTER o retorno e ignorado, mas a convencao e devolver NEW.
    RETURN NEW;
END;
$$;

-- IS DISTINCT FROM, e nao <>: com <>, um UPDATE que grava o mesmo status geraria
-- linha de auditoria; e se um dos lados fosse NULL, a comparacao devolveria NULL
-- (nem verdadeiro nem falso) e o WHEN nunca dispararia.
CREATE TRIGGER trg_auditar_status_pedido
    AFTER UPDATE ON pedido
    FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION fn_auditar_status_pedido();
