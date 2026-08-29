# JFood — implementação de referência em Spring

Gabarito do **projeto JFood**, o aplicativo de delivery que o aluno constrói sozinho ao longo da
disciplina de **Bancos de Dados** da pós-graduação em Java. Uma etapa por aula, uma branch por
etapa, e os números de cada medição registrados em [`docs/`](docs).

Este repositório cobre apenas o caminho **Spring**. As partes opcionais da apostila em Quarkus,
Jakarta Data e Liquibase ficam de fora de propósito.

## As branches

As branches são **cumulativas**: cada uma continua de onde a anterior parou, então dá para entrar em
qualquer ponto da disciplina. `main` aponta para o estado final.

| Branch | Etapa | O que tem | Gabarito |
|---|---|---|---|
| `aula01` | Modelagem conceitual e lógica | MER, normalização até a 3FN, esquema lógico | [docs/aula01.md](docs/aula01.md) |
| `aula02` | Do esquema lógico ao banco vivo | `docker-compose.yml`, DDL, seed e as consultas do app | [docs/aula02.md](docs/aula02.md) |
| `aula03` | Camada de persistência Java | `jfood-administrativo` com Spring Data JPA | [docs/aula03.md](docs/aula03.md) |
| `aula04` | Versionando o esquema | Flyway no controle do banco | [docs/aula04.md](docs/aula04.md) |
| `aula05` | Concorrência no fluxo do pedido | Locks pessimista e otimista, `SKIP LOCKED`, `REQUIRES_NEW` | [docs/aula05.md](docs/aula05.md) |
| `aula06` | Inteligência no banco | Views, materialized view, PL/pgSQL, triggers e paginação | [docs/aula06.md](docs/aula06.md) |
| `aula07` | Dimensionando o sistema | Contas de guardanapo, CP/AP e shard key — só documento | [docs/aula07.md](docs/aula07.md) |
| `aula08` | Catálogo de restaurantes | `jfood-catalogo` com MongoDB e o Subset Pattern | [docs/aula08.md](docs/aula08.md) |
| `aula09` | Cache no horário do almoço | Redis no `jfood-catalogo`: Cache Aside, write-behind, stampede | [docs/aula09.md](docs/aula09.md) |
| `aula10` | Tracking de entregadores | `jfood-tracking` com Cassandra | [docs/aula10.md](docs/aula10.md) |
| `aula11` | Recomendação por avaliações | `jfood-recomendacoes` com Neo4J + arquitetura final | [docs/aula11.md](docs/aula11.md) |

## Os serviços

| Serviço | Banco | Porta | A partir da |
|---|---|---|---|
| [`jfood-administrativo`](jfood-administrativo) | PostgreSQL | 8080 | `aula03` |
| [`jfood-catalogo`](jfood-catalogo) | MongoDB + Redis | 8082 | `aula08` |
| [`jfood-tracking`](jfood-tracking) | Cassandra | 8083 | `aula10` |
| [`jfood-recomendacoes`](jfood-recomendacoes) | Neo4J | 8084 | `aula11` |

Cada um é um projeto Maven independente, com o próprio `mvnw`. Requer **Java 25** e **Docker**.

## Subindo tudo

A infraestrutura inteira vem de um `docker-compose.yml` único na raiz, que ganhou um serviço novo a
cada aula que precisou de um banco novo.

```bash
docker compose up -d
```

| Container | Porta | Cliente |
|---|---|---|
| `jfood-postgres` | 5432 | DBeaver — banco `jfood-db`, usuário e senha `postgres` |
| `jfood-mongodb` | 27017 | `mongosh -u admin -p adminpassword --authenticationDatabase admin` |
| `jfood-redis` | 6379 | `docker exec -it jfood-redis redis-cli` |
| `jfood-cassandra` | 9042 | `docker exec -it jfood-cassandra cqlsh` |
| `jfood-neo4j` | 7474 / 7687 | Neo4J Browser em `http://localhost:7474` — `neo4j` / `neo4jpassword` |

### Carregando os dados

O PostgreSQL se popula sozinho: o Flyway roda na subida do `jfood-administrativo`. Os outros três
precisam de uma carga inicial:

```bash
# MongoDB — catalogo de restaurantes e cardapios
docker cp mongo/seed-catalogo.js jfood-mongodb:/tmp/seed.js
docker exec jfood-mongodb mongosh --quiet -u admin -p adminpassword \
  --authenticationDatabase admin jfood_catalogo --file /tmp/seed.js

# Cassandra — keyspace e tabelas
docker exec -i jfood-cassandra cqlsh < cql/01-keyspace-e-tabelas.cql

# Neo4J — restricoes, o grafo derivado dos outros dois bancos, e o enriquecimento de demo
docker exec -i jfood-neo4j cypher-shell -u neo4j -p neo4jpassword < cypher/01-restricoes.cypher
python3 scripts/sincronizar-grafo.py | \
  docker exec -i jfood-neo4j cypher-shell -u neo4j -p neo4jpassword
docker exec -i jfood-neo4j cypher-shell -u neo4j -p neo4jpassword \
  < cypher/02-demo-avaliacoes-e-vinculos.cypher
```

### Rodando um serviço

```bash
cd jfood-administrativo && ./mvnw spring-boot:run
```

## Onde está o resto

| Pasta | O que tem |
|---|---|
| [`docs/`](docs) | O gabarito escrito de cada etapa, com as medições reais |
| [`docs/modelo/`](docs/modelo) | Os diagramas em Mermaid: MER, persistência poliglota e fluxo do pedido |
| [`postman/`](postman) | Uma collection por serviço, mais o environment `JFood — local` |
| [`sql/`](sql) | Scripts de cada etapa: DDL e seed da Etapa 2, e os experimentos de medição |
| [`mongo/`](mongo), [`cql/`](cql), [`cypher/`](cypher) | Carga e experimentos dos bancos NoSQL |
| [`scripts/`](scripts) | O pipeline que sincroniza o grafo a partir do PostgreSQL e do MongoDB |

> **Os scripts de `sql/aulaNN/` são medição, não esquema.** Eles criam volume ou objetos temporários,
> medem e desfazem. O que precisa existir em todo ambiente está nas migrações do Flyway.
