# Etapa 2 — Do esquema lógico ao banco vivo

Gabarito da Etapa 2 do JFood, correspondente à Aula 2 (SQL na Prática). Todos os números desta
página foram medidos rodando os scripts de [`sql/aula02/`](../sql/aula02) contra o container do
`docker-compose.yml`.

## Como reproduzir

```bash
docker compose up -d

docker exec -i jfood-postgres psql -U postgres -d jfood-db < sql/aula02/01-ddl.sql
docker exec -i jfood-postgres psql -U postgres -d jfood-db < sql/aula02/02-alter.sql
docker exec -i jfood-postgres psql -U postgres -d jfood-db < sql/aula02/03-seed.sql
docker exec -i jfood-postgres psql -U postgres -d jfood-db < sql/aula02/04-consultas.sql
docker exec -i jfood-postgres psql -U postgres -d jfood-db < sql/aula02/05-explain-like.sql
```

Pelo DBeaver, a conexão é `localhost:5432`, banco `jfood-db`, usuário e senha `postgres`.

---

## 1. Ambiente

O `docker-compose.yml` sobe um `postgres:16` com **volume próprio** (`jfood_postgres_data`) e
`healthcheck` com `pg_isready`. O volume é o que faz os dados sobreviverem a um
`docker compose down`; sem ele, cada `down` apagaria o seed.

> A Etapa 2 pede um serviço separado do `javify-db`. Aqui o JFood tem repositório próprio, então o
> serviço é o único do arquivo — mas o banco (`jfood-db`) e o volume continuam sendo dele, e não
> compartilhados com o projeto do curso.

## 2. DDL

`01-ddl.sql`, na ordem de dependência: primeiro `usuario` e `categoria_restaurante`, que não
dependem de ninguém; depois as três especializações; então `endereco_entrega`, `restaurante` e
`item_cardapio`; por fim `pedido` e seus dependentes.

O `CHECK` do `pedido.status` limita a `('CRIADO','CONFIRMADO','EM_PREPARO','A_CAMINHO','ENTREGUE',
'CANCELADO')`. Ele não é redundante com o enum que a Aula 3 vai declarar: o enum protege quem passa
pela aplicação, e o `CHECK` protege quem entra pelo DBeaver — que é exatamente por onde o dado
errado costuma entrar.

## 3. `ALTER TABLE` na prática

```sql
ALTER TABLE pedido ADD COLUMN observacao VARCHAR(255);
ALTER TABLE pedido ADD COLUMN taxa_entrega NUMERIC(10,2);
```

**Por que `taxa_entrega` não pode nascer `NOT NULL` sem `DEFAULT`.** A tabela já tem linhas, e todas
elas ficariam com `NULL` na coluna nova no instante do `ALTER`. O `NOT NULL` seria violado antes de
qualquer `INSERT`, e o PostgreSQL recusa:

```
ERROR:  column "taxa_entrega" of relation "pedido" contains null values
```

Restam dois caminhos. Um é nascer com `DEFAULT`, e aí o banco preenche as linhas antigas com ele —
mas escolher esse valor é uma **decisão de negócio**: dizer que todo pedido do passado teve taxa
`0,00` é uma afirmação sobre o passado, e alguém precisa assiná-la. O outro é nascer nullable e
virar obrigatória depois, em três passos — que é exatamente o que a Etapa 4 faz, com Flyway.

Aqui a coluna nasce **nullable**, de propósito, para deixar esse trabalho para a Etapa 4.

## 4. Seed

`03-seed.sql` carrega 19 usuários (6 donos, 3 entregadores, 10 clientes), 12 endereços, 5
categorias, **7 restaurantes**, 21 itens de cardápio, **20 pedidos**, 34 itens de pedido, 19
pagamentos e 13 avaliações.

Toda FK é resolvida por subquery sobre o nome, como no `INSERT` de `assinatura` da aula. A exceção
é `pedido.id`, inserido explicitamente — `item_pedido`, `pagamento` e `avaliacao` precisam apontar
para um pedido específico, e pedido não tem chave natural. Depois da carga vem a linha que quase
todo mundo esquece:

```sql
SELECT setval('pedido_id_seq', (SELECT MAX(id) FROM pedido));
```

Sem ela, a sequence continua no 1 e o primeiro `INSERT` da aplicação (Aula 3) estoura chave
duplicada — um bug que só aparece depois, longe do script que o causou.

Dois restaurantes nunca receberam pedido, de propósito:

| Restaurante | Cadastrado há | Serve para |
|---|---|---|
| Verde & Cia | 45 dias | Aparece no anti-join da consulta 6 |
| Forno & Pizza | 10 dias | Aparece no `LEFT JOIN` da consulta 4, mas **não** no anti-join |

O par existe para que o anti-join não passe por acaso: se o filtro de 30 dias fosse esquecido, o
resultado teria dois restaurantes em vez de um.

## 5. As consultas que o app precisa

### Ticket médio por restaurante

```
      restaurante      | total_pedidos | ticket_medio
-----------------------+---------------+--------------
 Sushi Yuki            |             4 |       128.33
 Cantina do Zé         |             5 |       119.00
 Pizzaria Bella Napoli |             5 |       117.64
 Sabor da Roça         |             3 |        94.73
 Esfiha do Khalil      |             2 |        82.55
```

Cancelados ficam de fora: não são receita. Repare que a Cantina do Zé aparece com **5** pedidos
aqui e com **6** na consulta 4 — a diferença é exatamente o pedido cancelado.

### Restaurantes com mais de 3 pedidos no período

```
      restaurante      | pedidos_no_periodo
-----------------------+--------------------
 Cantina do Zé         |                  6
 Pizzaria Bella Napoli |                  5
 Sushi Yuki            |                  4
```

**`WHERE` ou `HAVING`?** Os dois, cada um no seu papel:

| Cláusula | Filtra | O que vai nela |
|---|---|---|
| `WHERE` | **Linhas**, antes de qualquer agrupamento | O recorte de data (`data_pedido >= NOW() - 60 days`) |
| `HAVING` | **Grupos**, depois do `GROUP BY` | `COUNT(p.id) > 3` |

`COUNT(*) > 3` no `WHERE` nem chega a rodar: quando o `WHERE` é avaliado, nenhum grupo foi formado
e a contagem não existe. E jogar o filtro de data no `HAVING` funcionaria, mas agregaria linhas que
seriam descartadas depois — filtrar cedo é mais barato.

### Cliente, restaurante e valor de cada pedido

Dois `INNER JOIN` encadeados — e uma terceira junção que a Etapa 1 tornou necessária: o **nome do
cliente não mora em `cliente`**, mora em `usuario`, a tabela base da especialização. É o custo do
`JOINED` que a Etapa 1 aceitou pagar.

### Todos os restaurantes, inclusive os que nunca venderam

```
      restaurante      | total_pedidos | receita
-----------------------+---------------+---------
 Cantina do Zé         |             6 |  657.80
 Pizzaria Bella Napoli |             5 |  588.20
 Sushi Yuki            |             4 |  513.30
 Sabor da Roça         |             3 |  284.20
 Esfiha do Khalil      |             2 |  165.10
 Forno & Pizza         |             0 |       0
 Verde & Cia           |             0 |       0
```

Duas armadilhas em uma consulta só. O `INNER JOIN` faria os dois últimos sumirem. E `COUNT(*)`
contaria a linha que o `LEFT JOIN` fabrica com tudo nulo do lado direito, devolvendo **1** para quem
nunca vendeu — `COUNT(p.id)` ignora nulos e devolve o `0` correto.

### Clientes acima da média geral

```
     cliente      | pedidos | ticket_medio
------------------+---------+--------------
 João Vitor Ramos |       1 |       223.20
 Ana Lima         |       3 |       151.37
 Gabriela Dias    |       2 |       127.00
```

A subquery escalar `(SELECT AVG(valor_total) FROM pedido)` não é correlacionada — não menciona a
linha de fora —, então o PostgreSQL a executa **uma vez** para a consulta inteira, e não uma vez por
grupo.

### Anti-join: cadastrados há mais de 30 dias e sem nenhum pedido

```
 restaurante | cadastrado_em
-------------+---------------
 Verde & Cia | 2026-07-15
```

O script traz as duas formas — `NOT EXISTS` e `LEFT JOIN ... IS NULL` — e as duas devolvem a mesma
linha. A adotada é `NOT EXISTS`: lê como a frase em português e não tem a armadilha do `NOT IN`, que
devolve conjunto **vazio** se a subquery retornar um único `NULL`.

## 6. A armadilha proposital: `LIKE '%pizza%'` × `LIKE 'Pizza%'`

Com 7 restaurantes, os dois casos dão *Seq Scan* e não há nada para ver. `05-explain-like.sql` cria
200 mil restaurantes sintéticos e o índice **dentro de uma transação**, mede, e faz `ROLLBACK` — o
banco fica como estava (confirmado: `SELECT COUNT(*) FROM restaurante` continua devolvendo 7).

**Curinga dos dois lados:**

```
Seq Scan on restaurante  (cost=0.00..5068.09 rows=20 width=36)
                         (actual time=0.006..13.968 rows=402 loops=1)
  Filter: ((nome)::text ~~ '%Pizza%'::text)
  Rows Removed by Filter: 199605
Execution Time: 13.986 ms
```

**Curinga só à direita:**

```
Index Scan using idx_restaurante_nome_pattern on restaurante
                         (cost=0.42..8.44 rows=20 width=36)
                         (actual time=0.028..0.132 rows=201 loops=1)
  Index Cond: (((nome)::text ~>=~ 'Pizza'::text) AND ((nome)::text ~<~ 'Pizzb'::text))
Execution Time: 0.142 ms
```

| | `'%Pizza%'` | `'Pizza%'` |
|---|---|---|
| Plano | Seq Scan | Index Scan |
| Linhas descartadas pelo filtro | 199.605 | 0 |
| Tempo de execução | **13,986 ms** | **0,142 ms** |

Cerca de **98× mais rápido**, e a distância cresce com a tabela.

**A explicação.** Um índice B-tree guarda as chaves **ordenadas**, e busca ordenada só funciona se
você souber por onde a chave começa. `'Pizza%'` diz isso — e o planejador traduz o `LIKE` em uma
faixa: `nome >= 'Pizza' AND nome < 'Pizzb'`, visível no `Index Cond`. `'%Pizza%'` não diz nada sobre
o começo: "Pizza" pode estar no meio de qualquer nome, e a única forma de saber é olhar todos.

**O detalhe que quase ninguém vê:** o índice foi criado com `varchar_pattern_ops`. Um B-tree comum
ordena pela colação do banco (pt_BR, en_US), cuja ordem **não é** a ordem byte a byte dos
caracteres — e sem esse operador nem o `'Pizza%'` consegue usar o índice. É por isso que "criei o
índice e o `LIKE` continua lento" é uma queixa tão comum.

Para o caso do `'%pizza%'` — busca por palavra no meio do nome — a saída não é índice B-tree: é
*full-text search* com `to_tsvector`/`plainto_tsquery`, que é justamente a query nativa que o JFood
implementa na Etapa 3.

---

## Critério de pronto

Todas as consultas rodam sem erro, e para cada uma está registrado acima em que etapa da ordem
`FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY` cada cláusula atuou — com destaque para as
duas em que a ordem é a explicação inteira: o `HAVING` da consulta 2 e o `COUNT(p.id)` da consulta 4.
