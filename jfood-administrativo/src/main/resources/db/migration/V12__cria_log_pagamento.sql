-- =============================================================================
-- V12 — a trilha de auditoria de pagamento
--
-- Quando o gateway recusa, o suporte precisa saber por que. Esse registro tem de
-- sobreviver ao ROLLBACK da transacao principal -- e por isso ele e gravado em
-- transacao propria (REQUIRES_NEW), nao por causa do esquema.
--
-- Sem FK para pedido de proposito: a trilha registra tentativas, inclusive de
-- pedidos que acabaram nao existindo. Um log que pode ser derrubado por
-- integridade referencial nao e um log.
-- =============================================================================

CREATE TABLE log_pagamento (
    id          BIGSERIAL    PRIMARY KEY,
    pedido_id   BIGINT       NOT NULL,
    motivo      VARCHAR(255) NOT NULL,
    ocorrido_em TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_log_pagamento_pedido ON log_pagamento (pedido_id);
