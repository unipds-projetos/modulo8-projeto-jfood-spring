# Etapa 10 — Tracking de entregadores no Cassandra

Gabarito da Etapa 10 do JFood, correspondente à Aula 10. O serviço é o
[`jfood-tracking`](../jfood-tracking), na porta **8083**.

Este é o subsistema que **não cabe** no PostgreSQL — e a Etapa 7 já calculou por quê.

## Como reproduzir

```bash
docker compose up -d cassandra          # leva ~1 min para aceitar conexoes
docker exec -i jfood-cassandra cqlsh < cql/01-keyspace-e-tabelas.cql
cd jfood-tracking && ./mvnw spring-boot:run
```

---

## 1. Por que um microsserviço separado

**Bounded context.** "Onde está a moto agora" e "quanto custou este pedido" são domínios diferentes,
com linguagem, modelo e ciclo de vida próprios. O `jfood-tracking` não conhece pedido, pagamento nem
cliente: ele conhece coordenada, entregador e entrega — e é só disso que precisa.

**Perfis de carga opostos.** O administrativo faz ~500 escritas/s no pico, cada uma dentro de uma
transação ACID. O tracking faz **40.000 escritas/s**, todas *append-only* e sem transação. Uma
aplicação que tenta ser boa nos dois é ruim nos dois: o pool de conexões, o *tuning* de GC, o tamanho
do heap e a estratégia de retry são configurações **conflitantes**.

**Escalabilidade independente.** O tracking escala com o número de entregadores em corrida; o
administrativo, com o número de pedidos. Juntos, escalar um obriga a escalar o outro — e a conta é
paga em máquina ociosa.

**Isolamento de falhas.** É a razão que decide, e a pergunta do enunciado é explícita:

> **Se o Cassandra sobrecarregar, o cliente ainda consegue fazer um pedido?**

**Sim.** O `jfood-administrativo` não fala com o Cassandra, não importa nenhuma classe do tracking e
não tem dependência de rede com ele. Um cluster de tracking fora do ar degrada **uma** funcionalidade
— a moto para de se mexer no mapa — enquanto pedido, pagamento e despacho continuam funcionando.

No monólito, a resposta seria **não**: a ingestão de GPS saturaria o pool de conexões, o WAL e a CPU
do mesmo PostgreSQL que grava pedidos, e o subsistema que gera receita cairia por causa de um
subsistema de conveniência.

## 2. Keyspace e modelagem Query-First

**Antes de escrever CQL, escrevemos as perguntas:**

1. *"onde está o entregador X agora?"*
2. *"qual foi a rota completa da entrega Y?"*
3. *"por onde o entregador X passou no dia D?"*

Elas **não** são atendidas pela mesma tabela. No relacional, uma tabela serve a muitas consultas
porque o índice conserta o que a modelagem não previu. Aqui a partição decide em qual **nó** o dado
mora — e a consulta que não respeita a partição ou faz *scatter-gather* no anel inteiro, ou não roda.

Então: **uma tabela por pergunta**, e a duplicação é assumida.

| Pergunta | Tabela | Partition key | Clustering | Tamanho da partição |
|---|---|---|---|---|
| 1 | `posicao_atual_entregador` | `entregador_id` | — | 1 linha (upsert) |
| 2 | `rota_por_entrega` | `entrega_id` | `instante ASC` | 180 a 720 pontos (a corrida) |
| 3 | `rota_por_entregador_dia` | `(entregador_id, dia)` | `instante DESC` | 4.320 pontos (a jornada) |

Um ping vira **três escritas**. Isso não é desperdício: no Cassandra a escrita é barata — vai para o
commitlog e a memtable, sequencialmente — e a leitura de uma partição errada é cara ou impossível.
Medido no cluster local: **`Local write latency: 0,005 ms`**.

> **`replication_factor: 1` e `SimpleStrategy` servem apenas para a máquina local.** Em produção,
> `NetworkTopologyStrategy` com fator 3: cada coordenada gravada em três racks físicos distintos.

## 3. A tabela de rota e a ordenação sem `ORDER BY`

```
PRIMARY KEY ( (entregador_id, dia) , instante )
              ^^^^^^^^^^^^^^^^^^^    ^^^^^^^^
              Partition Key          Clustering Key
              (em qual nó?)          (em que ordem no disco?)
```

Os parênteses **internos** delimitam a partition key. E o `CLUSTERING ORDER BY (instante DESC)` grava
o disco **já ordenado**.

Cinco pings inseridos fora de ordem, lidos no `cqlsh` **sem nenhum `ORDER BY`**:

```
 entregador_id | dia        | instante                        | velocidade_kmh
---------------+------------+---------------------------------+----------------
             7 | 2026-08-29 | 2026-08-29 16:54:08.413000+0000 |           32.5
             7 | 2026-08-29 | 2026-08-29 12:05:00.000000+0000 |             50
             7 | 2026-08-29 | 2026-08-29 12:04:00.000000+0000 |             40
             7 | 2026-08-29 | 2026-08-29 12:03:00.000000+0000 |             30
             7 | 2026-08-29 | 2026-08-29 12:02:00.000000+0000 |             20
             7 | 2026-08-29 | 2026-08-29 12:01:00.000000+0000 |             10
```

Do mais recente para o mais antigo — que é o que "onde ele está agora" precisa: a resposta é a
**primeira linha**, sem sort e sem varredura.

E a mesma rota, na tabela por entrega (`ASC`), sai do começo ao fim — que é o que o mapa precisa para
desenhar o traçado. **Duas ordens diferentes para os mesmos dados**, porque são duas perguntas
diferentes.

## 4. O anti-padrão da partição, calculado

Números medidos no cluster local com `nodetool tablestats`: **36,5 bytes por linha** em disco (já
comprimido).

**Se a partição fosse só `entregador_id`:**

```
linhas por dia    = 6 h × 3.600 / 5 s     =     4.320
linhas por ano    = 4.320 × 365           = 1.576.800
tamanho em 1 ano  =                             58 MB
tamanho em 3 anos =                            173 MB
```

Uma partição de **1,5 milhão de linhas** e **58 MB por entregador, por ano** — e crescendo para
sempre. Os problemas, em ordem de gravidade:

- **A partição não se divide.** Ela vive inteira em um nó (e nas suas réplicas). Nenhum
  rebalanceamento a quebra. Adicionar nós ao anel **não** alivia o nó que carrega o entregador mais
  antigo.
- **Compaction cara.** Reescrever repetidamente uma partição de dezenas de MB consome I/O que
  deveria estar servindo leitura.
- **Leitura imprevisível.** "Os pontos de hoje" tem de percorrer uma partição que contém três anos de
  histórico.

**Com bucketing por `(entregador_id, dia)`:**

```
linhas por partição = 4.320
tamanho             = 154 KB
```

Limitada **por construção** — uma jornada de trabalho — e espalhada por 200 mil chaves ativas
simultâneas. É a resposta que a Etapa 7 já tinha antecipado.

### O custo, medido

"Por onde o entregador passou nos últimos 7 dias" deixa de ser **uma** leitura e passa a ser
**sete**, montadas pela aplicação:

```
GET /api/v1/tracking/entregadores/1000/rota-ultimos-dias?dias=7
{ "dias": 7, "particoesLidas": 7, "pontos": 1487, "duracaoMs": 66 }
```

66 ms para sete partições. É a troca que se aceita: **sete leituras rápidas e previsíveis contra uma
leitura de uma partição que cresce para sempre**.

> **O que não se deve fazer** é um `IN` com os sete dias em uma consulta só. O coordenador teria de
> falar com vários nós e esperar o mais lento — o *scatter-gather* que o Cassandra existe para
> evitar. Sete consultas paralelas da aplicação são melhores que uma consulta que espera sete nós.

## 5. TTL em vez de `DELETE`

A rota bruta só interessa por 90 dias. `USING TTL` no insert — via
`InsertOptions.builder().ttl(Duration.ofDays(90))`:

```
 instante                        | ttl_segundos | ttl_lat
---------------------------------+--------------+---------
 2026-08-29 16:54:08.413000+0000 |      7775984 | 7775984
 2026-08-29 12:05:00.000000+0000 |      7775992 | 7775992
```

90 dias = 7.776.000 s. Cada **célula** nasce com o próprio prazo.

**Por que um `DELETE` em massa criaria uma tempestade de tombstones.** No Cassandra, `DELETE` **não
apaga**: ele *escreve* um marcador dizendo "isto foi apagado". O dado só some de verdade na próxima
compaction, depois de `gc_grace_seconds` (10 dias por padrão) — a janela que garante que uma réplica
que estava fora do ar não "ressuscite" o registro.

Um job noturno com `DELETE FROM ... WHERE dia < ?` gravaria **centenas de milhões de tombstones** de
uma vez. E tombstone é lido: toda consulta a uma partição afetada percorre os marcadores antes de
devolver o que sobrou. O sintoma é uma leitura que fica cada vez mais lenta até estourar o
`tombstone_failure_threshold` e a consulta **parar de responder** — no meio da madrugada, sem que
ninguém tenha mudado código.

Com TTL, o vencimento também vira tombstone — mas **espalhado no tempo e por partição**, em vez de
concentrado em uma tempestade.

> **A `posicao_atual_entregador` não leva TTL**, e é deliberado: ela é sobrescrita a cada ping
> (`upsert`), nunca acumula, e um TTL ali faria o entregador sumir do mapa 90 dias depois de parar de
> trabalhar. `TTL(velocidade_kmh)` nessa tabela devolve `null`.

## 6. O serviço Spring Data Cassandra

`@PrimaryKeyClass` como record, com `PARTITIONED` e `CLUSTERED` — os dois papéis do CQL, agora
declarados em Java:

```java
@PrimaryKeyClass
public record RotaPorEntregadorDiaChave(
        @PrimaryKeyColumn(name = "entregador_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        Long entregadorId,
        @PrimaryKeyColumn(name = "dia", type = PrimaryKeyType.PARTITIONED, ordinal = 1)
        LocalDate dia,
        @PrimaryKeyColumn(name = "instante", type = PrimaryKeyType.CLUSTERED,
                          ordinal = 2, ordering = Ordering.DESCENDING)
        Instant instante
) implements Serializable {}
```

**`Instant`, e não `LocalDateTime`.** O Cassandra armazena `TIMESTAMP` como instante UTC.
`LocalDateTime` não carrega fuso — e entregadores em fusos diferentes é o caso **normal** em um app
nacional. Com `LocalDateTime`, um ping de Manaus e outro de São Paulo no mesmo segundo apareceriam
com uma hora de diferença na rota, e a auditoria de tempo de entrega ficaria errada exatamente onde
ela é usada para resolver disputa de pagamento.

**O endpoint de ingestão responde `202 Accepted`**, e não `201`. O app do entregador dispara isso a
cada 5 segundos e não espera nada de volta: não há recurso a devolver, não há `Location`, e ele não
vai tratar erro. `202` diz exatamente isso — "recebi, vou processar, siga sua vida".

**`schema-action=NONE`.** O esquema pertence ao `cql/01-keyspace-e-tabelas.cql`, e não ao Spring.
`CREATE_IF_NOT_EXISTS` geraria tabelas a partir das anotações — e as decisões que importam aqui
(bucketing, clustering order, TTL, estratégia de replicação) **não cabem em anotação**.

### O erro que todo mundo toma na primeira subida

```
IllegalStateException: Since you provided explicit contact points, the local DC must be
explicitly set. Current DCs in this cluster are: datacenter1
```

O driver **exige** que você declare o datacenter local quando informa contact points — é uma proteção
contra rotear tráfego para outra região sem querer. Descubra o valor no próprio cluster:

```sql
SELECT cluster_name, data_center FROM system.local;
```

## 7. Teste de carga

100 mil pontos — **300 mil escritas**, três por ping — pelo mesmo caminho do endpoint de ingestão, em
um Cassandra de **um nó** dentro do Docker, em um laptop:

| Concorrência | Duração | Pontos/s | **Escritas/s** | Falhas |
|---|---|---|---|---|
| 32 | 13,7 s | 7.302 | 21.907 | 0 |
| 64 | 7,6 s | 13.126 | 39.378 | 0 |
| 128 | **5,8 s** | **17.266** | **51.797** | 0 |

Zero falhas em todas as rodadas.

### Comparando com a estimativa da Etapa 7

A Etapa 7 estimou **40.000 pontos/s** no pico. O experimento entrega **17.266 pontos/s** em **um nó
em container**, dividindo CPU com o PostgreSQL, o MongoDB e o Redis na mesma máquina.

```
40.000 / 17.266 ≈ 2,3 nós
```

Com folga operacional e replicação, o cluster de produção começaria em **6 nós** — três para a
capacidade com margem, e o fator de replicação 3 que triplica a escrita real. É um cluster modesto
para 864 milhões de pontos por dia.

O contraste com o PostgreSQL é o argumento inteiro da etapa: 40.000 escritas/s são 3 a 8 vezes o teto
de um primário **e não há como dividir** — um primário é um primário. No Cassandra, a resposta para
"não está aguentando" é acrescentar nós ao anel, e a partition key `(entregador_id, dia)` garante que
os novos nós recebam trabalho de verdade.

> **Um número que vale ler duas vezes:** `Local write latency: 0,005 ms`. A escrita no Cassandra é um
> append sequencial no commitlog mais uma inserção em memória. Não há árvore para reequilibrar, não
> há página para ler antes de escrever, não há `VACUUM`. É por isso que a conta fecha.

---

## Critério de pronto

Toda consulta do serviço acessa **uma única partição** — e cada tabela tem uma pergunta de negócio
que a motivou:

| Endpoint | Partições lidas | Pergunta |
|---|---|---|
| `GET /entregadores/{id}/posicao` | 1 | "onde está o entregador agora?" — o app do cliente, a cada poucos segundos |
| `GET /entregas/{id}/rota` | 1 | "qual foi a rota da entrega?" — auditoria de tempo e disputa de pagamento |
| `GET /entregadores/{id}/rota?dia=` | 1 | "por onde ele passou no dia D?" — operação e suporte |
| `GET /entregadores/{id}/rota-ultimos-dias` | 7 | a mesma pergunta em janela maior — **sete leituras de uma partição cada**, e nunca um scan |
