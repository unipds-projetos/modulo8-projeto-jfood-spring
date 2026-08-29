# Etapa 11 — Recomendação cruzada por avaliações no Neo4J

Gabarito da Etapa 11 do JFood, correspondente à Aula 11. O serviço é o
[`jfood-recomendacoes`](../jfood-recomendacoes), na porta **8084**.

Última etapa: o JFood ganha a feature que fecha o ciclo — *"recomende restaurantes para a Ana com
base no que outros clientes, que avaliaram bem os mesmos restaurantes que ela, também avaliaram
bem"*.

## Como reproduzir

```bash
docker compose up -d
docker exec -i jfood-neo4j cypher-shell -u neo4j -p neo4jpassword < cypher/01-restricoes.cypher

# o pipeline: le o PostgreSQL e o MongoDB, emite Cypher
python3 scripts/sincronizar-grafo.py | \
  docker exec -i jfood-neo4j cypher-shell -u neo4j -p neo4jpassword

# enriquecimento de demonstracao (NAO faz parte do pipeline)
docker exec -i jfood-neo4j cypher-shell -u neo4j -p neo4jpassword \
  < cypher/02-demo-avaliacoes-e-vinculos.cypher

cd jfood-recomendacoes && ./mvnw spring-boot:run
```

O Neo4J Browser fica em `http://localhost:7474` — `neo4j` / `neo4jpassword`.

---

## 1. O relacional sofre? A medição honesta

A mesma pergunta, escrita em SQL sobre as tabelas do JFood
([`sql/aula11/recomendacao-em-sql.sql`](../sql/aula11)):

```sql
  FROM avaliacao a1
 INNER JOIN avaliacao a2 ON a2.restaurante_id = a1.restaurante_id ...   -- salto 2
 INNER JOIN avaliacao a3 ON a3.cliente_id     = a2.cliente_id     ...   -- salto 3
 INNER JOIN restaurante r ON r.id = a3.restaurante_id
 WHERE a1.cliente_id = :cliente                                         -- salto 1
   AND NOT EXISTS (... avaliacao ...) AND NOT EXISTS (... pedido ...)
```

**4 JOINs e 2 subconsultas correlacionadas** — três da tabela `avaliacao` com ela mesma, um para
trazer o nome. Cada salto do grafo virou um self-join.

Volume: **5.000 clientes, 200.013 avaliações**, com os índices que qualquer DBA criaria
(`(cliente_id, nota)` e `(restaurante_id, nota)`). O mesmo dado carregado no Neo4J.

### Os números, em dois formatos de grafo

| Cenário | Restaurantes | Avaliadores por restaurante | **SQL** | **Cypher** |
|---|---|---|---|---|
| **Denso** | 100 | ~2.000 | **121 ms** | **502 ms** |
| **Esparso** | 3.000 | ~67 | **20 ms** | **27 ms** |

**Não é o resultado que o enunciado antecipa — e é o resultado.**

Em três saltos, com este volume, o PostgreSQL **empata ou ganha**. E há uma explicação, visível nos
dois planos:

- No cenário **denso**, cada restaurante tem 2.000 avaliadores, e a travessia explode para ~115 mil
  caminhos (`AntiSemiApply ... 114747` no `PROFILE`). O Neo4J percorre relacionamento por
  relacionamento; o PostgreSQL joga o problema em dois *Parallel Hash Join* sobre uma tabela de 200
  mil linhas — que é exatamente para o que um hash join foi feito. **Fan-out largo favorece o
  relacional.**
- No cenário **esparso**, os dois caem para a mesma ordem de grandeza. Nenhum dos dois sofre.

> **A lição vale mais que o número esperado:** três saltos não são profundos. Uma travessia de três
> saltos em uma tabela de 200 mil linhas, com índice, é trabalho para o qual o PostgreSQL foi
> construído. Escolher Neo4J *só* por causa desta consulta seria adicionar um banco por hábito.

### Onde o grafo ganha de verdade: profundidade

A mesma base, outra pergunta — *"qual o caminho mais curto entre dois clientes, através de
restaurantes que ambos avaliaram?"*:

| | Consulta | Tempo |
|---|---|---|
| **Cypher** | `shortestPath((a)-[:AVALIOU*..6]-(b))` | **~3 ms** (94 ms na primeira execução, fria) |
| **SQL** | `WITH RECURSIVE` fazendo BFS até 3 níveis | **348 / 360 / 341 ms** |

**Cerca de 100× a favor do grafo** — e a diferença cresce com a profundidade. No plano do
PostgreSQL, o `Recursive Union` produz **258.145 linhas por iteração** para devolver 783: ele expande
toda a fronteira porque não tem como parar quando encontra o destino.

E há a diferença que não aparece em milissegundos: no Cypher, mudar de 6 para 10 saltos é trocar
`*..6` por `*..10`. Em SQL, é reescrever a consulta.

**A conclusão para o JFood:** o Neo4J não se justifica pela recomendação de três saltos — se ela
fosse a única feature de grafo, um `WITH RECURSIVE` bem escrito no PostgreSQL resolveria. Ele se
justifica quando o produto pedir profundidade variável: "quem você talvez conheça", caminhos de
influência, detecção de fraude em componentes conectados. É uma decisão sobre o **roadmap**, não
sobre esta consulta.

## 2. O modelo do grafo

| Nó | Origem do id | O que guarda |
|---|---|---|
| `(:Cliente)` | PostgreSQL (`cliente.usuario_id`) | `id`, `nome` |
| `(:Restaurante)` | MongoDB (`_id`) | `id`, `nome` |
| `(:Categoria)` | MongoDB | `nome` |
| `(:Prato)` | MongoDB (`item_id`) | `id`, `nome`, `tags` |

| Aresta | Propriedades |
|---|---|
| `[:AVALIOU]` | **`nota`**, `data` |
| `[:PEDIU]` | `vezes` |
| `[:DO_TIPO]`, `[:SERVE]`, `[:SEGUE]` | — |

### Por que a nota vai na aresta

A nota **não é propriedade do cliente** ("a nota da Ana") nem **do restaurante** ("a nota da
Cantina"): ela só existe no **encontro** dos dois. É a mesma natureza do `quantidade` e do
`preco_unitario` do `item_pedido` na Etapa 1 — um relacionamento N:N **com atributo**.

A alternativa seria um nó intermediário `(:Avaliacao)` ligando cliente e restaurante. Funcionaria, e
custaria **um salto a mais em toda travessia** — em uma consulta de três saltos, isso vira seis, e
a fan-out dobra de estágios. A aresta com propriedade é exatamente o que o *Property Graph Model*
existe para expressar; usar um nó ali seria modelar em grafo com a cabeça de tabela associativa.

Em Spring Data Neo4J:

```java
@RelationshipProperties
public record Avaliou(@RelationshipId String id, @TargetNode Restaurante restaurante,
                      int nota, LocalDate data) {}
```

Três anotações, três papéis: `@RelationshipProperties` marca a aresta, `@TargetNode` diz para onde
ela aponta, `@RelationshipId` é a identidade interna. Já `[:SEGUE]`, que não tem propriedade, é só
uma `List<Cliente>` com `@Relationship`.

## 3. IDs, não cópias

O grafo é um **banco derivado**: nada nasce nele.
[`scripts/sincronizar-grafo.py`](../scripts/sincronizar-grafo.py) lê o PostgreSQL e o MongoDB e emite
o Cypher correspondente.

```
MERGE (r:Restaurante {id: '6a9303dda6d8524125d1a7bb'}) SET r.nome = 'Cantina do Zé';
MATCH (r:Restaurante {id: '6a93...'}), (c:Categoria {nome: 'Italiana'}) MERGE (r)-[:DO_TIPO]->(c);
MERGE (c:Cliente {id: 10}) SET c.nome = 'Ana Lima';
MATCH (c:Cliente {id: 10}), (r:Restaurante {id: '6a93...'})
MERGE (c)-[av:AVALIOU]->(r) SET av.nota = 5, av.data = date('2026-07-03');
```

Resultado da sincronização: 10 clientes, 7 restaurantes, 21 pratos, 5 categorias; 29 `AVALIOU`, 18
`PEDIU`, 21 `SERVE`, 7 `DO_TIPO`, 7 `SEGUE`.

**Três decisões no script:**

- **`MERGE`, e não `CREATE`.** Rodar duas vezes produz o mesmo grafo que rodar uma. Sem isso, um
  batch reexecutado depois de uma falha duplicaria tudo — e um grafo com nós duplicados dá
  recomendações silenciosamente erradas.
- **Só `id` e `nome`.** Preço, taxa e horário ficam no MongoDB. Se morassem aqui, existiriam **três**
  cópias do catálogo — Mongo, cache do Redis e grafo — e três oportunidades de divergir. O nome vem
  junto só para o Neo4J Browser ficar legível; nenhuma decisão de negócio é tomada a partir dele.
- **Constraints de unicidade sobre o id de origem** ([`cypher/01-restricoes.cypher`](../cypher)). São
  elas que impedem o batch de criar um segundo nó para o mesmo cliente — e, de quebra, criam o índice
  que a travessia usa para achar o ponto de partida.

## 4. A consulta, construída em passos

[`cypher/03-recomendacao-em-passos.cypher`](../cypher) tem os seis blocos para rodar um de cada vez
no Neo4J Browser. Em grafo, a visualização não é enfeite: é a ferramenta de diagnóstico.

```cypher
MATCH (ana:Cliente {nome: 'Ana Lima'})-[a1:AVALIOU]->(:Restaurante)
      <-[a2:AVALIOU]-(vizinho:Cliente)-[a3:AVALIOU]->(sugestao:Restaurante)
WHERE a1.nota >= 4 AND a2.nota >= 4 AND a3.nota >= 4
  AND vizinho <> ana
  AND NOT (ana)-[:AVALIOU]->(sugestao)
  AND NOT (ana)-[:PEDIU]->(sugestao)
RETURN sugestao.nome AS restaurante, count(DISTINCT vizinho) AS forcaRecomendacao
ORDER BY forcaRecomendacao DESC LIMIT 5;
```

**O `MATCH` é o desenho do grafo.** Os três saltos estão em uma linha e meia, e a direção das setas
diz quem avaliou quem. O SQL equivalente da seção 1 tem 4 JOINs para dizer a mesma coisa.

**O `WHERE NOT` com padrão** — `NOT (ana)-[:AVALIOU]->(sugestao)` — é uma cláusula só. Em SQL, dois
`NOT EXISTS` com subconsultas correlacionadas.

Antes do filtro (passo 4) e depois (passo 5):

```
       antes                              depois
 Esfiha do Khalil      4          Esfiha do Khalil       4
 Sabor da Roça         4          Sabor da Roça          4
 Verde & Cia           3          Verde & Cia            3
 Pizzaria Bella Napoli 2          Pizzaria Bella Napoli  2
 Forno & Pizza         2          Forno & Pizza          2
 Cantina do Zé         1   <-- ela ja avaliou
 Sushi Yuki            1   <-- ela ja avaliou
```

## 5. O ranking enriquecido

Em vez de **contar** vizinhos, somar as notas ponderadas e desempatar por proximidade de categoria:

```cypher
sum((a2.nota - 3) * (a3.nota - 3)) AS pesoTotal
```

O peso `(nota - 3)` transforma a escala 1–5 em −2..+2: uma nota 5 de quem também deu 5 no
restaurante-base vale **+4**; uma nota 4 de quem deu 4 vale **+1**. Contar vizinhos trata as duas
como iguais.

| Por contagem | vizinhos | peso | | Por peso | vizinhos | peso |
|---|---|---|---|---|---|---|
| Esfiha do Khalil | 4 | 13 | | Esfiha do Khalil | 4 | 13 |
| Sabor da Roça | **4** | 10 | | **Verde & Cia** | 3 | **11** |
| Verde & Cia | 3 | **11** | | Sabor da Roça | 4 | 10 |
| Forno & Pizza | 2 | 4 | | Forno & Pizza | 2 | 4 |
| Pizzaria Bella Napoli | 2 | 3 | | Pizzaria Bella Napoli | 2 | 3 |

**A diferença:** o Verde & Cia sobe uma posição. Ele tem **menos** vizinhos (3 contra 4), mas
vizinhos que deram nota **5** tanto no restaurante-base quanto nele. O Sabor da Roça tem mais gente
recomendando, com entusiasmo menor.

Qual está certo? **Depende do produto.** Contagem favorece consenso e é mais robusta a um único
avaliador entusiasmado; peso favorece intensidade e descobre nichos. Para a tela inicial de um
delivery — onde o cliente vê cinco cards e não vai ler nenhuma justificativa — o peso tende a
produzir recomendações mais surpreendentes, e a surpresa é metade do valor de uma recomendação. Por
isso o endpoint `/ponderado` existe: a decisão é de teste A/B, não de arquitetura.

## 6. A segunda consulta: detecção de fraude

Contas diferentes que compartilham cartão, endereço ou dispositivo, formando um componente conectado
suspeito.

**A decisão de modelagem é que cartão e dispositivo viram NÓS**, e não propriedades do cliente. É o
que torna a pergunta respondível como travessia:

```cypher
MATCH (c1:Cliente)-[:PAGA_COM|ACESSA_DE]->(vinculo)<-[:PAGA_COM|ACESSA_DE]-(c2:Cliente)
WHERE elementId(c1) < elementId(c2)
RETURN c1.nome, c2.nome, labels(vinculo)[0], coalesce(vinculo.token, vinculo.id)
```

```
Diego Alves   x Elisa Rocha     Cartao       tok_zzz999
Diego Alves   x Elisa Rocha     Dispositivo  dev-suspeito-01
Diego Alves   x Isabela Prado   Cartao       tok_zzz999
Elisa Rocha   x Isabela Prado   Cartao       tok_zzz999
```

Três contas, um cartão, dois dispositivos — um componente conectado que nenhuma delas declarou.

Em SQL isso seria um self-join sobre uma tabela de meios de pagamento: possível. Mas a pergunta
**seguinte** — "e quem está conectado a essas contas por sua vez, e a essas?" — exigiria outro nível
de join, e o próximo, mais um. Aqui é trocar o padrão por `[:PAGA_COM|ACESSA_DE*..4]`. **É a
profundidade variável de novo**, e é ela que justifica o grafo.

## 7. O serviço

`@Node` nos records de domínio, `@Relationship(type = ..., direction = OUTGOING)` nas ligações,
`@RelationshipProperties` no `AVALIOU` — e a consulta devolve uma **projeção**, e não a entidade:

```java
public record RecomendacaoRestaurante(String restauranteId, String nome, String categoria,
                                      long forcaRecomendacao, long pesoTotal) {}
```

A consulta não precisa hidratar o grafo inteiro — são quatro colunas. É a diferença entre uma
resposta de algumas centenas de bytes e uma travessia completa do objeto, com todas as avaliações de
todos os clientes envolvidos. Os aliases do `RETURN` casam com os componentes do record; errar um
deles quebra o mapeamento.

E `$clienteId` é parâmetro nomeado do Cypher — concatenar string aqui abriria a mesma porta de
injeção da Etapa 3.

O endpoint devolve **204** quando não há recomendação: um cliente novo, sem avaliações, não tem
vizinhança — e o app precisa distinguir "não há recomendação para você" de "há zero restaurantes".

## 8. O pipeline que mantém o grafo atualizado

**Batch noturno**, e não CDC. A justificativa é a tolerância a defasagem da feature.

A pergunta que decide é: *o que quebra se este dado estiver 12 horas atrasado?*

| Dado | Se atrasar 12 h | Pipeline |
|---|---|---|
| Saldo, pagamento, estoque de cupom | **Quebra** — cobra errado, vende o que não tem | Nunca sai do PostgreSQL |
| Status do pedido | Quebra — o cliente vê a informação errada | Leitura do primary (Etapa 7) |
| **Recomendação por avaliações** | **Não quebra** — a recomendação de ontem continua útil | **Batch noturno** |

Uma recomendação de ontem ainda é uma boa recomendação; um saldo de ontem não é um saldo. E o custo
da alternativa é real: CDC significa Debezium lendo o WAL do PostgreSQL e o oplog do MongoDB, uma
fila entre os dois, e um consumidor idempotente — **três componentes novos para operar** só para
tornar a recomendação algumas horas mais fresca.

**Quando o batch deixaria de servir:** se a recomendação passar a considerar o comportamento **da
sessão atual** ("você acabou de olhar japonesa, veja estes"). Aí a defasagem passa a ser o produto, e
o pipeline muda junto. Hoje não é o caso.

O script atual é o batch: `MERGE` idempotente, seguro para reexecutar, sem estado entre execuções.
A evolução natural é filtrar por `criado_em > :ultimaExecucao` para não reprocessar o histórico
inteiro toda noite.

---

# O documento final de arquitetura do JFood

| Subsistema | Banco | O que ganha | **O que sacrifica** |
|---|---|---|---|
| Pedidos, pagamentos, usuários | **PostgreSQL** | Transação multi-tabela, FK, `SELECT FOR UPDATE`, `@Version`, views, funções e triggers. Tudo o que as Etapas 5 e 6 usaram para garantir **exatamente um pagamento** | **Escala de escrita em um nó só.** Não há sharding nativo: quando 514 linhas/s virarem 50.000, a resposta é comprar uma máquina maior — e um dia acaba |
| Catálogo e cardápios | **MongoDB** | Opcionais com formato próprio por restaurante; a tela inteira em **uma leitura de um documento**; sharding nativo | **Integridade referencial e transação entre documentos.** O Subset Pattern só funciona com a disciplina de propagar toda alteração — e uma propagação que falha deixa dado errado sem ninguém saber |
| Cache do cardápio e contadores | **Redis** | Latência abaixo de 1 ms; absorve o pico de 2.000 req/s da tela inicial; write-behind que tira o `UPDATE` do caminho quente | **Durabilidade.** Até 5 min de contadores se perdem entre flushes. E toda invalidação esquecida é dado velho servido com cara de novo, por até 60 minutos |
| Tracking de entregadores | **Cassandra** | **40.000 escritas/s**, escala horizontal de verdade, TTL que estabiliza 1 TB/mês, `Local write latency: 0,005 ms` | **Consulta que não foi prevista na modelagem.** Sem partition key, ou não roda ou faz scatter-gather. Não há `JOIN`, não há agregação ad-hoc, e mudar de ideia sobre a chave significa recarregar a tabela |
| Recomendação por avaliações | **Neo4J** | Travessia de profundidade variável em milissegundos: caminho mais curto **100× mais rápido** que o `WITH RECURSIVE` equivalente | **É banco derivado.** Nada nasce nele, então ele está sempre atrasado em relação à verdade — e precisa de um pipeline que alguém mantenha |

## O JFood, no estágio em que está, justifica os cinco?

**Não. Justifica três.**

**PostgreSQL — inegociável.** Um pedido confirmado com o pagamento revertido é prejuízo com nome de
cliente. As garantias que as Etapas 5 e 6 usaram não são "nice to have": são o produto.

**Cassandra — inegociável, e a conta é da Etapa 7.** 40.000 pontos/s são **78×** a escrita do
subsistema transacional, e 3 a 8× o teto de um primário PostgreSQL. Não há ajuste que feche essa
conta em um nó. E a razão que decide não é desempenho: é **isolamento de falhas** — se o tracking
derrubasse o banco de pedidos, um subsistema de conveniência teria derrubado o que gera receita.

**Redis — justificado pela conta, não pela moda.** 120 núcleos sem cache contra 6 com 95% de hit
rate. É o melhor retorno por unidade de complexidade dos cinco: uma dependência simples, um modelo
mental de duas operações, e um ganho de vinte vezes.

**MongoDB — defensável, não inevitável.** Os opcionais de cardápio são um caso real de schema
flexível, e o argumento de *write amplification* do `JSONB` é verdadeiro. Mas o JFood tem **7
restaurantes**, não 50 mil — e um `JSONB` no PostgreSQL levaria o produto bem além do estágio atual,
com uma dependência a menos, uma migração a menos e o catálogo dentro da mesma transação do resto.
**Quando ele passaria a ser inevitável:** quando o catálogo não couber em um nó, ou quando a
reescrita de documento no `UPDATE` aparecer no monitoramento. Nenhum dos dois aconteceu.

**Neo4J — não se justifica hoje.** A Etapa 11 mediu: em três saltos, o PostgreSQL **ganha** no
cenário denso (121 ms contra 502 ms) e empata no esparso (20 ms contra 27 ms). A recomendação, como
está especificada, cabe em um `WITH RECURSIVE`. O Neo4J passa a valer no dia em que o produto pedir
**profundidade variável** — "quem você talvez conheça", cadeias de influência, fraude em componentes
conectados — e aí o ganho é de duas ordens de grandeza. É uma decisão de **roadmap**, e a resposta
honesta hoje é "ainda não".

## O custo que a tabela não mostra

Cinco bancos são cinco sistemas para operar, monitorar, versionar, manter consistentes e sobre os
quais formar equipe. Só o PostgreSQL tem Flyway; nos outros quatro, mudança de esquema é script
manual e disciplina. O Redis é derivado do MongoDB, o Neo4J é derivado dos dois — e **todo dado
derivado pode divergir**, o que significa alguém escrevendo e mantendo reconciliação.

E o maior risco não é técnico: ninguém é sênior em cinco bancos. Ou o time carrega cinco
especialidades rasas, ou o conhecimento se concentra em uma pessoa.

> Não existe bala de prata em arquitetura, apenas trade-offs. O objetivo desta disciplina não foi
> aprender a usar cinco bancos. Foi aprender a **escolher** — e a saber dizer, com número, quando a
> resposta é "este aqui, ainda não".
