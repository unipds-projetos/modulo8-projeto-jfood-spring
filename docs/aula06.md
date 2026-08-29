# Etapa 6 — Inteligência no banco

Gabarito da Etapa 6 do JFood, correspondente à Aula 6. Fecha o módulo PostgreSQL — e responde à
pergunta que ficou em aberto na Etapa 1 sobre `valor_total`.

Todos os tempos desta página foram medidos com `EXPLAIN ANALYZE` no PostgreSQL 16 em container, com
**200.022 pedidos e 400.036 itens** carregados por
[`sql/aula06/prepara-volume.sql`](../sql/aula06).

## Como reproduzir

```bash
docker compose down -v && docker compose up -d
cd jfood-administrativo && ./mvnw spring-boot:run

# em outro terminal, para as medições que precisam de volume
docker exec -i jfood-postgres psql -U postgres -d jfood-db < sql/aula06/prepara-volume.sql
docker exec -i jfood-postgres psql -U postgres -d jfood-db < sql/aula06/valor-total-tres-formas.sql
docker exec -i jfood-postgres psql -U postgres -d jfood-db < sql/aula06/limpa-volume.sql
```

---

## 1. A view de operação, e por que ela é `R__`

[`R__v_pedidos_em_andamento.sql`](../jfood-administrativo/src/main/resources/db/migration) junta
pedido, cliente, restaurante, categoria, entregador e endereço, filtrando os quatro status ativos.

**A escolha por repetível é sobre custo de reaplicação.** Uma view **não guarda dado**: reaplicá-la
é reescrever uma definição — instantâneo, idempotente com `CREATE OR REPLACE`, e ela volta
atualizada. Quando o time acrescentar uma coluna à tela do painel, edita o arquivo e sobe. Se cada
ajuste exigisse uma `V__` nova, a linha do tempo viraria um cemitério de versões do mesmo objeto.

> **E a materialized view não é `R__` pelo motivo oposto** — está na seção 2.

## 2. A materialized view de faturamento

`V17` cria `mv_faturamento_por_restaurante` com pedidos entregues, receita total, ticket médio e a
coluna `atualizado_em`, mais o índice único obrigatório.

### Os dois tempos

| | Tempo |
|---|---|
| Query original (JOIN + `GROUP BY` sobre 200 mil pedidos) | **167,474 ms** |
| Leitura do snapshot (`SELECT * FROM mv_...`) | **0,026 ms** |
| `REFRESH ... CONCURRENTLY` (ida e volta pelo endpoint) | 112 ms |

Cerca de **6.400× mais rápido** na leitura. E o custo do JOIN é pago **uma vez**, no refresh, por um
job — e não a cada abertura do painel, disputando I/O e CPU com cancelamentos, logins e pagamentos
de clientes reais.

### Por que ela é `V__` e não `R__`

A materialized view **guarda dado**. Um `DROP` + `CREATE` dela reexecuta o JOIN inteiro e derruba os
índices junto. Fazer isso a cada edição do arquivo, em toda subida de toda instância, é caro e
imprevisível — e o `REFRESH`, que é a operação que ela realmente precisa, é trabalho de job
agendado, não de migração.

A assimetria com a view da seção 1 é a lição: **a escolha entre `V__` e `R__` não é sobre o tipo do
objeto, é sobre o custo de reaplicá-lo.**

### `CONCURRENTLY` e o detalhe que derruba muita gente

O índice único não é opcional: sem ele, o PostgreSQL recusa o `CONCURRENTLY`. E sem `CONCURRENTLY`, o
`REFRESH` adquire lock exclusivo e **bloqueia todas as leituras** enquanto reconstrói — exatamente o
que a materialized view existia para evitar.

Há um segundo detalhe, e ele não está na documentação óbvia:

```
REFRESH MATERIALIZED VIEW CONCURRENTLY cannot run inside a transaction block
```

Um `@Modifying @Query` com `@Transactional` **não funciona**. `FaturamentoService.atualizarSnapshot()`
por isso pega a conexão do `DataSource` e executa em autocommit, sem `@Transactional`. É o detalhe
que faz muita gente desistir do `CONCURRENTLY` e voltar para o `REFRESH` que trava as leituras.

**A coluna `atualizado_em`** existe para que o consumidor saiba **quão velho** é o dado. Sem ela, um
refresh travado passa despercebido e o painel exibe números antigos como se fossem de agora.

> **Uma armadilha que este gabarito pisou.** A projeção sobre a materialized view declarava
> `OffsetDateTime getAtualizadoEm()`. A consulta é **nativa**, e o Hibernate devolve o `timestamptz`
> como `java.time.Instant` — e **projeção por interface não converte tipos**, só repassa o que veio.
> Resultado: a consulta roda, a lista volta preenchida, e a serialização estoura só na saída:
>
> ```
> HttpMessageNotWritableException: Could not write JSON: Cannot project java.time.Instant
> to java.time.OffsetDateTime; Target type is not an interface and no matching Converter found
> ```
>
> Um HTTP 500 que o Spring MVC registra como **WARN**, sem stack trace no log de erro. Nas entidades
> JPA (`@Column`) a conversão acontece; em projeção sobre query nativa, não. O tipo declarado tem de
> ser o que o driver devolve.

## 3. A função PL/pgSQL de taxa de entrega

`V13` acrescenta `pedido.distancia_km` (o insumo que faltava); `V14` cria a função com faixa
progressiva — cada quilômetro custa o preço da **sua** faixa, como imposto de renda:

| Faixa | Preço |
|---|---|
| até 2 km | R$ 5,90 (base) |
| de 2 a 6 km | + R$ 1,50/km |
| de 6 a 12 km | + R$ 1,00/km |
| acima de 12 km | + R$ 0,70/km |

Chamada do Spring por query nativa (`SELECT calcular_taxa_entrega(:pedidoId)`):

```
pedido 18 | CRIADO     |  1,10 km -> {"taxaEntrega":5.90}   [200]
pedido 11 | EM_PREPARO |  8,20 km -> {"taxaEntrega":14.10}  [200]
pedido 15 | A_CAMINHO  | 11,00 km -> {"taxaEntrega":16.90}  [200]
pedido 1  | ENTREGUE              -> {"erro":"Pedido 1 ja foi entregue; a taxa nao pode mais ser recalculada"}  [409]
pedido 999 (inexistente)          -> {"erro":"Pedido 999 nao encontrado"}  [404]
```

**Por que essa regra pertence ao banco:** ela é lida por mais de um consumidor — a API, o painel de
operações e o relatório financeiro — e todos precisam da **mesma** faixa. Reimplementar em três
lugares é como garantir que os três vão divergir.

**Por que o `USING ERRCODE` importa.** O `RAISE EXCEPTION ... USING ERRCODE = 'P0002'` só vale alguma
coisa se alguém **ler** o código do outro lado. Sem a tradução em `PedidoService`, os dois erros da
função viram o mesmo 500 genérico, e o cliente da API não distingue "pedido não existe" de "pedido já
entregue". A tradução lê o **SQLSTATE**, e não a mensagem: a mensagem muda com o idioma do servidor e
com o próximo refactor; o código, não.

> **O que NÃO pertence ao banco:** regra que muda toda semana, ou que precisa de teste unitário
> rápido. PL/pgSQL não tem o ferramental de teste que o Java tem.

## 4. O trigger de auditoria

`V15` cria a tabela `auditoria_pedido` (imutável — `REVOKE UPDATE, DELETE`, `TIMESTAMPTZ`,
`current_user`) e o trigger `AFTER UPDATE`.

**A prova pedida pelo enunciado: alterar o status direto no DBeaver, sem passar pela aplicação.**
Três `UPDATE` seguidos, feitos por `psql`:

| `UPDATE` | Gerou auditoria? |
|---|---|
| `SET status='EM_PREPARO'` (era `CRIADO`) | **sim** |
| `SET observacao='mexi so na observacao'` | não |
| `SET status='EM_PREPARO'` de novo (mesmo valor) | não |

```
 pedido_id | status_antigo | status_novo | alterado_por |   alterado_em
-----------+---------------+-------------+--------------+-----------------
        18 | CRIADO        | EM_PREPARO  | postgres     | 15:55:22.567996
```

Uma linha para três `UPDATE`. Quem filtrou foi a cláusula `WHEN (OLD.status IS DISTINCT FROM
NEW.status)`.

**`IS DISTINCT FROM`, e não `<>`.** Com `<>`, um `UPDATE` que regrava o mesmo status geraria linha de
auditoria — e, se um dos lados fosse `NULL`, a comparação devolveria `NULL` (nem verdadeiro nem
falso) e o `WHEN` **nunca** dispararia. `IS DISTINCT FROM` trata `NULL` como valor comparável.

**Por que trigger e não código de aplicação:** o `UPDATE` feito direto no DBeaver às duas da manhã
também precisa ser registrado. Auditoria na aplicação audita a **aplicação**; auditoria no banco
audita o **dado**. E `alterado_por` grava `current_user` — o log da aplicação diria "sistema".

## 5. O trigger `BEFORE` de validação

`V16`: um entregador não pode ter mais de uma entrega **em andamento**.

```
entregas em andamento: pedido 15, Rafael Souza, A_CAMINHO

UPDATE pedido SET status='A_CAMINHO', entregador_id=<Rafael> WHERE id=11;
ERROR:  Entregador 7 ja tem uma entrega em andamento
CONTEXT:  PL/pgSQL function fn_validar_entrega_unica() line 13 at RAISE

UPDATE pedido SET status='A_CAMINHO', entregador_id=<Tiago> WHERE id=11;
UPDATE 1
```

**A cláusula `WHEN` é o que impede a validação de custar em toda escrita.** Sem
`WHEN (NEW.status = 'A_CAMINHO' AND NEW.entregador_id IS NOT NULL)`, **todo** `UPDATE` em `pedido` —
inclusive o recálculo de `valor_total` e a marcação de `despachado_em` — pagaria uma consulta a mais.

### A condição de corrida do meu próprio trigger

O trigger não é seguro sob concorrência, e é importante saber por quê.

Duas transações despacham pedidos diferentes para o **mesmo** entregador ao mesmo tempo:

| | Transação A (pedido 30) | Transação B (pedido 31) |
|---|---|---|
| t1 | `SELECT COUNT(*) ... A_CAMINHO` → **0** | |
| t2 | | `SELECT COUNT(*) ... A_CAMINHO` → **0** |
| t3 | grava `A_CAMINHO`, `COMMIT` | |
| t4 | | grava `A_CAMINHO`, `COMMIT` |

As duas leram **antes** de a outra gravar, as duas viram zero, as duas passaram. O entregador
termina com duas entregas em andamento — e o trigger não emitiu erro nenhum.

É **a mesma armadilha do duplo pagamento da Etapa 5**, e a razão é idêntica: existe uma janela entre
ler e decidir. `READ COMMITTED` não fecha essa janela; ele só garante que ninguém leu dado sujo.

**Como fechá-la.** Três caminhos, do mais barato ao mais forte:

1. **Índice único parcial** — a solução certa aqui, porque transforma a regra em uma invariante que
   o banco garante sem lock explícito:

   ```sql
   CREATE UNIQUE INDEX uq_entregador_entrega_unica
       ON pedido (entregador_id)
    WHERE status = 'A_CAMINHO';
   ```

   A segunda transação recebe violação de unicidade no `COMMIT`. O índice é pequeno (só as entregas
   em andamento) e a garantia é absoluta.

2. **Lock pessimista na linha do entregador** antes de validar — `SELECT ... FROM entregador WHERE
   usuario_id = ? FOR UPDATE`. Serializa os despachos do mesmo entregador. Funciona, custa espera, e
   depende de **todo** caminho de escrita lembrar de adquirir o lock.

3. **`SERIALIZABLE`** na transação de despacho. O PostgreSQL detecta a dependência e aborta uma das
   duas — correto, e o mais caro dos três.

> **A lição maior:** um trigger `BEFORE` que valida com `SELECT` é uma verificação
> *read-then-write*, e verificações desse tipo **nunca** são atômicas sozinhas. Ele documenta a
> regra e dá uma mensagem de erro legível; quem garante a regra é a constraint.

## 6. Paginação — e a medição

As duas telas do JFood que crescem sem limite são o **histórico de pedidos de um cliente** e as
**avaliações de um restaurante**. As duas foram expostas com `Pageable` e ordenação explícita.

**A ordenação não é enfeite.** Ela é por `(dataPedido DESC, id DESC)`, e o `id` é o desempate: sem um
critério determinístico, dois pedidos do mesmo instante podem aparecer na página 2 **e** na 3, e
outro em nenhuma das duas. O sintoma é cruel porque não é um erro — é uma lista que "às vezes repete
um pedido".

### Página 1 × página 5.000, no banco

Com 200.003 pedidos da Ana Lima:

| Consulta | Linhas lidas | Tempo |
|---|---|---|
| **Offset**, página 1 (`LIMIT 20 OFFSET 0`) | 20 | **0,082 ms** |
| **Offset**, página 5.000 (`LIMIT 20 OFFSET 99980`) | **100.000** | **17,014 ms** |
| `COUNT(*)` que toda `Page` paga | 200.003 | 9,958 ms |
| **Keyset**, página 1 | 20 | **0,055 ms** |
| **Keyset**, página 5.000 | 20 | **0,113 ms** |

**De onde vem a diferença**, lida no `EXPLAIN ANALYZE`:

```
-- OFFSET, página 5.000
Limit  (actual time=16.992..16.995 rows=20 loops=1)
  ->  Index Scan using idx_pedido_historico_cliente on pedido
        (actual time=0.011..14.770 rows=100000 loops=1)
```

O `rows=100000` do nó de baixo é a resposta inteira: o banco **leu e descartou** 100 mil linhas para
devolver 20. E o custo cresce linearmente com o número da página.

```
-- KEYSET, mesma posição
Limit  (actual time=0.044..0.048 rows=20 loops=1)
  ->  Index Scan using idx_pedido_historico_cliente on pedido
        Index Cond: ((cliente_id = 10) AND (ROW(data_pedido, id) < ROW('2024-03-10 02:02:02+00', 100000)))
        (actual time=0.043..0.046 rows=20 loops=1)
```

`rows=20`. A comparação de tupla virou **`Index Cond`** — o banco desceu o B-tree direto ao ponto. O
custo é o mesmo na página 1 e na 5.000: **0,055 ms × 0,113 ms**, contra os 0,082 ms × 17,014 ms do
offset. Um fator de **150×** na página 5.000, que continua crescendo.

> **A comparação de tupla é o detalhe que faz funcionar.** `(data_pedido, id) < (:ultimaData,
> :ultimoId)` é uma comparação lexicográfica de verdade, não `data < :d AND id < :i`. Comparar só a
> data pularia ou repetiria os pedidos do mesmo instante — e é assim que o keyset "quase certo"
> perde registros em silêncio.

### Pelos endpoints

| Endpoint | Tempo (ida e volta) |
|---|---|
| `/pedidos/pagina?page=0&size=20` | 20,5 ms |
| `/pedidos/pagina?page=4999&size=20` | 59,7 ms |
| `/pedidos/keyset` (página 1) | 6,0 ms |
| `/pedidos/keyset` (página 5.000) | 5,9 ms |

O offset paga o `OFFSET` **e** o `COUNT(*)`; o keyset não paga nenhum dos dois. Em compensação, o
keyset não devolve `totalElements` — e não dá para "ir para a página 500". Se o produto exige esse
salto, offset é a escolha certa, em uma base pequena. **A decisão é de arquitetura, não de gosto.**

> A resposta usa `PagedModel` e não `PageImpl`, por causa de
> `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` na classe da aplicação. Serializar
> `PageImpl` direto expõe uma classe interna do Spring no JSON público da API: se um campo interno
> mudar de nome numa atualização, todo app mobile e front-end quebra. A apostila trata disso na Aula
> 8; aqui já está ligado, porque um gabarito não deveria distribuir uma serialização instável.

## 7. A decisão sobre `valor_total`

As três implementações, medidas com
[`sql/aula06/valor-total-tres-formas.sql`](../sql/aula06) — que cria as duas alternativas dentro de
uma transação, mede e faz `ROLLBACK`.

### Leitura: uma página de 20 pedidos do histórico

| Implementação | Tempo |
|---|---|
| (a) e (b) — a coluna | **0,134 ms** |
| (c) — a view sob demanda | **332,086 ms** |

```
-- (c) a view
Limit  (actual time=332.000..332.008 rows=20 loops=1)
  ->  GroupAggregate  (actual time=0.029..226.588 rows=200022 loops=1)
        ->  Index Scan on item_pedido  (actual time=0.008..37.611 rows=400036 loops=1)
```

O `GroupAggregate` **agregou os 200.022 pedidos** para devolver 20. É a natureza do problema: o
`LIMIT` não pode ser empurrado para dentro de uma agregação que ainda não terminou. **2.500× mais
caro** — para mostrar uma tela.

### Escrita: inserir 1.000 itens de pedido

| Implementação | Tempo total | Do qual, o trigger |
|---|---|---|
| (a) sem trigger — aplicação recalcula em uma passada | **11,465 ms** | — |
| (b) com trigger `AFTER INSERT/UPDATE/DELETE` | **46,273 ms** | **31,487 ms** em 1.000 chamadas |

O trigger sozinho custou quase **três vezes** o `INSERT` inteiro. E é 1 `UPDATE` em `pedido` por
**linha** de item: um pedido com 8 itens paga 8 recálculos completos do mesmo total.

### A tabela de decisão

| | (a) aplicação | (b) trigger | (c) view |
|---|---|---|---|
| **Frescor** | Depende da disciplina do código | Sempre correto | Sempre correto, por definição |
| **Custo de leitura** | 0,134 ms | 0,134 ms | 332 ms |
| **Custo de escrita** | 11,5 ms / 1.000 itens | 46,3 ms / 1.000 itens | zero |
| **Risco de divergência** | **Real** — e já aconteceu | Nenhum pelo banco | Impossível |

### A escolha, e a honestidade sobre ela

**Fica (a): coluna materializada mantida pela aplicação** — a decisão da Etapa 1, confirmada.

O que decide não é o desempenho de escrita: (b) custa 4× mais, mas 46 ms para 1.000 itens é
irrelevante no volume do JFood. O que decide é **o que `valor_total` significa**.

`valor_total` é **o valor cobrado do cliente**. Ele inclui a taxa de entrega e o desconto do cupom da
Etapa 5, e passa por arredondamento. A soma dos itens é um **insumo** do total, não o total. Tanto
(b) quanto (c) redefinem `valor_total` como "a soma dos itens mais a taxa" — e, no dia em que o cupom
entrar no cálculo, os dois passam a devolver um número que **não é o que foi cobrado no cartão**.
Isso é divergência contábil, não otimização.

E o risco de (a) é real: ele apareceu na Etapa 3, quando a remoção de um item sem `orphanRemoval`
deixou o pedido 1 valendo R$ 117,70 com R$ 133,70 em itens. A mitigação não é trocar de estratégia, é
**concentrar a escrita em um lugar só** — `Pedido.recalcularValorTotal()`, chamado dentro da mesma
transação que grava os itens — e ter uma consulta de conciliação no relatório noturno.

> **O trigger tem um caso em que ganha:** quando existe mais de um caminho de escrita fora do seu
> controle — uma carga em lote, um script de migração de dados, outro serviço mexendo na mesma
> tabela. Aí o custo de 4× compra a garantia de que ninguém esquece. No JFood, todo caminho de
> escrita passa pelo `jfood-administrativo`.

## 8. Fechamento do módulo relacional

Em seis etapas o JFood ganhou modelagem em 3FN, esquema versionado, uma camada de persistência com
o N+1 sob controle, concorrência tratada nas duas filosofias e inteligência dentro do banco. Tudo
isso em **um** PostgreSQL, e ele aguenta bem mais do que o JFood tem hoje.

Onde ele vai parar, e quem ataca cada limite:

| Limite que o JFood vai encontrar | Por quê | Aula |
|---|---|---|
| **O cardápio não cabe em tabelas** | Cada restaurante tem opcionais diferentes — tamanho, ponto da carne, adicionais. Em relacional isso vira ou uma tabela EAV, ou 40 colunas nulas, ou um `JSONB` que o PostgreSQL reescreve inteiro a cada `UPDATE` (*write amplification*) | 8 — MongoDB |
| **O pico do almoço** | Das 11h30 às 13h a tela inicial é lida milhares de vezes por segundo, e o cardápio muda uma vez por semana. Ler disco para devolver a mesma resposta é desperdício estrutural | 9 — Redis |
| **O GPS dos entregadores** | Um ping a cada 5 s por entregador em corrida. É ingestão massiva, append-only, com leitura sempre por uma chave só — o oposto do que uma B-tree transacional otimiza | 10 — Cassandra |
| **A recomendação por avaliações** | "Clientes que gostaram do que você gostou também gostaram de..." é uma travessia de três saltos. Em SQL são JOINs encadeados cujo custo explode com a profundidade | 11 — Neo4J |

O que **fica** no PostgreSQL: usuários, pedidos e pagamentos. Não por inércia — porque é exatamente
onde as garantias que ele dá são inegociáveis. Um pedido confirmado com o pagamento revertido é um
prejuízo; um cardápio desatualizado por 30 segundos é um inconveniente. **ACID é caro, e vale o preço
onde o erro custa dinheiro.**

O contraponto honesto vem na Etapa 7: cinco bancos são cinco sistemas para operar, monitorar,
versionar, manter consistentes e sobre os quais formar equipe. O documento de arquitetura que fecha o
JFood precisa responder se o projeto, no estágio em que está, justifica os cinco.

---

## Critério de pronto

Toda a inteligência criada aqui está versionada em migração — `V13` a `V17` e
`R__v_pedidos_em_andamento` — e nenhuma parte dela existe apenas "no banco da sua máquina":

```
docker compose down -v && docker compose up -d
cd jfood-administrativo && ./mvnw spring-boot:run
Successfully applied 19 migrations to schema "public", now at version v17
```

Os scripts de `sql/aula06/` são **medição**, não esquema: eles criam volume ou objetos temporários,
medem e desfazem. O que precisa existir em todo ambiente está nas migrações.
