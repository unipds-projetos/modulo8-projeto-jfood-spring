-- =============================================================================
-- V10 — a coluna de versao para controle otimista
--
-- DEFAULT 0 e obrigatorio: as 22 linhas que ja existem precisam de um valor
-- inicial, senao o primeiro UPDATE com "WHERE versao = ?" nao encontra nada e o
-- Hibernate acusa conflito onde nao ha.
--
-- Esta coluna e gerenciada pelo JPA. Nunca a altere a mao.
-- =============================================================================

ALTER TABLE pedido
    ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
