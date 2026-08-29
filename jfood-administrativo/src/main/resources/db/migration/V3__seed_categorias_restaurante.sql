-- =============================================================================
-- V3 — as categorias de cozinha
--
-- Dado de dominio, e nao dado de teste: sem estas cinco linhas a aplicacao nao
-- consegue cadastrar restaurante nenhum. Por isso vive numa migracao, e nao num
-- script de seed que alguem roda a mao.
-- =============================================================================

INSERT INTO categoria_restaurante (nome) VALUES
    ('Italiana'),
    ('Japonesa'),
    ('Brasileira'),
    ('Árabe'),
    ('Vegana');
