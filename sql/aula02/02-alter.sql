-- =============================================================================
-- JFood — Etapa 2, passo 2: ALTER TABLE
--
-- O time de produto pediu duas colunas novas depois que o esquema ja existia.
-- Cirurgia no esquema ao vivo: sem recriar a tabela e sem perder dados.
-- =============================================================================

ALTER TABLE pedido ADD COLUMN observacao VARCHAR(255);

-- Por que taxa_entrega NAO pode nascer NOT NULL sem DEFAULT:
-- a tabela ja tem linhas. Um NOT NULL sem DEFAULT exige que TODA linha existente
-- ja tenha valor -- e elas tem NULL no instante do ALTER. O PostgreSQL recusa:
--
--   ERROR:  column "taxa_entrega" of relation "pedido" contains null values
--
-- Ou a coluna nasce nullable (e vira NOT NULL depois, em tres passos -- e o que
-- a Etapa 4 faz com Flyway), ou nasce com DEFAULT, e ai o banco preenche as
-- linhas antigas com ele. Escolher o DEFAULT e uma decisao de negocio: dizer que
-- todo pedido antigo teve taxa 0,00 e uma afirmacao sobre o passado, nao um detalhe
-- tecnico. Aqui a coluna nasce nullable, de proposito.
ALTER TABLE pedido ADD COLUMN taxa_entrega NUMERIC(10,2);
