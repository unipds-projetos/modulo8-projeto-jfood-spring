-- =============================================================================
-- V11 — cupons de desconto
--
-- O cenario da etapa: 100 unidades para 50 mil clientes. E contencao maxima --
-- todo mundo disputando as MESMAS 100 linhas, no mesmo minuto.
-- =============================================================================

CREATE TABLE cupom (
    id                  BIGSERIAL      PRIMARY KEY,
    codigo              VARCHAR(30)    NOT NULL UNIQUE,
    valor_desconto      NUMERIC(10,2)  NOT NULL CHECK (valor_desconto > 0),
    quantidade_total    INT            NOT NULL CHECK (quantidade_total > 0),
    quantidade_resgatada INT           NOT NULL DEFAULT 0 CHECK (quantidade_resgatada >= 0),
    valido_ate          TIMESTAMPTZ    NOT NULL,
    -- O banco tambem garante a invariante: nem que a aplicacao erre, o estoque
    -- nao fica negativo.
    CONSTRAINT ck_cupom_estoque CHECK (quantidade_resgatada <= quantidade_total)
);

CREATE TABLE cupom_resgate (
    id           BIGSERIAL    PRIMARY KEY,
    cupom_id     BIGINT       NOT NULL REFERENCES cupom(id),
    cliente_id   BIGINT       NOT NULL REFERENCES cliente(usuario_id),
    resgatado_em TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Um cupom por cliente. Esta e a segunda linha de defesa: mesmo que dois
    -- requests do mesmo cliente passem pelo lock, o UNIQUE derruba o segundo.
    CONSTRAINT uq_cupom_cliente UNIQUE (cupom_id, cliente_id)
);

INSERT INTO cupom (codigo, valor_desconto, quantidade_total, valido_ate)
VALUES ('ALMOCO10', 10.00, 100, NOW() + INTERVAL '30 days');
