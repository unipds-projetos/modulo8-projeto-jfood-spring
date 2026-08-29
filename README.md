# JFood — implementação de referência em Spring

Gabarito do **projeto JFood**, o sistema de delivery que o aluno constrói sozinho ao longo da
disciplina de **Bancos de Dados** da pós-graduação em Java. Uma etapa por aula, uma branch por
etapa.

Este repositório cobre apenas o caminho **Spring**. As partes opcionais da apostila em Quarkus,
Jakarta Data e Liquibase ficam de fora de propósito.

## As branches

As branches são **cumulativas**: cada uma continua de onde a anterior parou.

| Branch | Etapa | O que tem |
|---|---|---|
| `aula01` | Modelagem conceitual e lógica | MER, normalização até a 3FN, esquema lógico |
| `aula02` | Do esquema lógico ao banco vivo | `docker-compose.yml`, DDL, seed e as consultas do app |
| `aula03` | Camada de persistência Java | `jfood-administrativo` com Spring Data JPA |
| `aula04` | Versionando o esquema | Flyway no controle do banco |
| `aula05` | Concorrência no fluxo do pedido | Locks pessimista e otimista, `SKIP LOCKED` |
| `aula06` | Inteligência no banco | Views, função PL/pgSQL, triggers e paginação |
| `aula07` | Dimensionando o sistema | Contas de guardanapo, CP/AP e shard key |
| `aula08` | Catálogo de restaurantes | `jfood-catalogo` com MongoDB |
| `aula09` | Cache no horário do almoço | Redis no `jfood-catalogo` |
| `aula10` | Tracking de entregadores | `jfood-tracking` com Cassandra |
| `aula11` | Recomendação por avaliações | `jfood-recomendacoes` com Neo4J |

## Os serviços

| Serviço | Banco | Porta |
|---|---|---|
| `jfood-administrativo` | PostgreSQL | 8080 |
| `jfood-catalogo` | MongoDB + Redis | 8082 |
| `jfood-tracking` | Cassandra | 8083 |
| `jfood-recomendacoes` | Neo4J | 8084 |

## Como rodar

A infraestrutura inteira vem de um `docker-compose.yml` único na raiz, que ganha um serviço novo
a cada aula que precisa de um banco novo.

```bash
docker compose up -d
```

Depois, o serviço da aula em questão:

```bash
cd jfood-administrativo && ./mvnw spring-boot:run
```

Requer **Java 25** e **Docker**. O Maven vem no wrapper de cada serviço.

## Onde está o resto

- `docs/aulaNN.md` — o gabarito escrito de cada etapa, com as medições reais (contagem de
  queries, `EXPLAIN ANALYZE`, tempos de página).
- `docs/modelo/` — os diagramas em Mermaid.
- `postman/` — uma collection por serviço, mais o environment.
