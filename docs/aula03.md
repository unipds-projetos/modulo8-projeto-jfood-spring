# Etapa 3 — A camada de persistência com Spring Data JPA

Gabarito da Etapa 3 do JFood, correspondente à primeira metade da Aula 3. O serviço é o
[`jfood-administrativo`](../jfood-administrativo): **Spring Boot 4.1.0** sobre **Java 25**.

Todos os números desta página foram medidos rodando a aplicação contra o `jfood-db` com o seed da
Etapa 2 (20 pedidos, 34 itens).

## Como reproduzir

```bash
docker compose up -d
cd jfood-administrativo && ./mvnw spring-boot:run
```

As requisições estão em [`postman/jfood-administrativo.postman_collection.json`](../postman), pasta
"Aula 3". Para contar as queries de um endpoint, conte as linhas `Hibernate:` que ele produz no log:

```bash
curl -s http://localhost:8080/api/v1/clientes/10/pedidos > /dev/null
```

O cliente `10` é a Ana Lima, com três pedidos em dois restaurantes e três entregadores distintos.

---

## 1. O projeto

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/jfood-db
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
```

**`validate`** porque o esquema é o da Etapa 2, criado à mão, e queremos a conferência: se uma
entidade divergir do banco, a aplicação **não sobe**. A partir da Etapa 4, com o Flyway no controle,
passa para `none`.

**`open-in-view=false`** não está no enunciado, mas é o que torna o exercício honesto. Com o padrão
(`true`), a sessão fica aberta até o fim da requisição e o *lazy loading* continua funcionando na
camada web — o N+1 acontece do mesmo jeito, só que espalhado por serialização de JSON, onde ninguém
procura. Com `false`, cada carregamento tem de ser deliberado.

Startup medido: **2,0 s** (`Started JfoodAdministrativoApplication in 2.048 seconds`).

## 2. As entidades

Sete entidades mais as três especializações e a `CategoriaRestaurante` que a 3FN criou. As regras
que o enunciado cobra:

| Regra | Onde está |
|---|---|
| `BigDecimal` em todo valor monetário | `ItemCardapio.preco`, `ItemPedido.precoUnitario`, `Pedido.valorTotal`, `Pedido.taxaEntrega`, `Pagamento.valor` |
| `@Enumerated(EnumType.STRING)` espelhando o `CHECK` | `Pedido.status`, `Entregador.tipoVeiculo`, `Pagamento.metodo` e `.status` |
| `ItemPedido` com `cascade = ALL` e `orphanRemoval = true` | `Pedido.itens` |
| `pedido.adicionarItem(item)` atualizando os dois lados | `Pedido.adicionarItem` |

**A especialização virou `@Inheritance(strategy = JOINED)`** em `Usuario`, com
`@PrimaryKeyJoinColumn(name = "usuario_id")` nas três subclasses — a tradução direta da decisão da
Etapa 1. Não há coluna discriminadora: no `JOINED`, o Hibernate descobre o tipo pelas tabelas
filhas.

**`EnumType.STRING`, sempre.** O padrão da JPA é `ORDINAL`, que grava o índice (0, 1, 2). Inserir
`CONFIRMADO` no meio do enum faria todo `A_CAMINHO` gravado virar `EM_PREPARO` — silenciosamente.

## 3. O N+1, medido

O endpoint é `GET /api/v1/clientes/10/pedidos`. Ele lista três pedidos e, **dentro do laço**, lê
`pedido.getRestaurante().getNome()` e `pedido.getEntregador().getNome()`.

| Versão | Queries |
|---|---|
| Primeira tentativa (com o lado inverso do pagamento mapeado) | **9** |
| Sem o lado inverso, ainda com lazy loading no laço | **6** |
| Com `JOIN FETCH` | **1** |

### De onde vêm as 6

```
1  select ... from pedido where cliente_id = ? order by data_pedido desc
2  select ... from restaurante where id = ?          <- Cantina do Zé
3  select ... from entregador join usuario ...       <- Bruna Alves
4  select ... from restaurante where id = ?          <- Sushi Yuki
5  select ... from entregador join usuario ...       <- Tiago Lima
6  select ... from entregador join usuario ...       <- Rafael Souza
```

São 1 + 2 restaurantes + 3 entregadores. Repare que os pedidos 1 e 4 são os dois da Cantina do Zé e
geraram **uma** query só: o contexto de persistência é um mapa de identidade, e a segunda referência
ao mesmo restaurante é resolvida em memória.

Isso não ameniza o problema — **desloca-o**. O entregador, que é diferente em cada pedido, custou
uma query por pedido. Com 300 pedidos no histórico de um cliente antigo e um entregador diferente em
cada um, são 301 idas ao banco para montar uma tela.

### As 3 queries a mais da primeira tentativa

A versão inicial de `Pedido` tinha isto:

```java
@OneToOne(mappedBy = "pedido", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
private Pagamento pagamento;
```

E o log mostrou, entre as outras, **três** consultas que ninguém pediu:

```
select p1_0.id, p1_0.metodo, p1_0.pago_em, p1_0.pedido_id, p1_0.status, p1_0.valor
  from pagamento p1_0 where p1_0.pedido_id = ?
```

Uma por pedido carregado — mesmo com `fetch = LAZY`, mesmo sem ninguém chamar `getPagamento()`.

**O motivo:** o lado **inverso** de um `@OneToOne` não consegue ser lazy. Quem tem a FK é
`Pagamento`; para devolver `null` ou um proxy no campo `Pedido.pagamento`, o Hibernate precisa saber
se a linha existe — e a única forma de saber é consultar. `FetchType.LAZY` ali é uma dica que o
provider não tem como cumprir sem *bytecode enhancement*.

A correção foi **apagar o campo**. Ele não servia a nada: quem precisa do pagamento usa o
`PagamentoRepository`. É o caso concreto do que a apostila diz sobre lados inversos serem opcionais
— só que aqui o lado inverso não era neutro, era um terço do problema.

> **Lição para levar:** todo `@OneToOne(mappedBy = ...)` é um `SELECT` por entidade pai carregada,
> sempre, para sempre. Antes de declarar um, pergunte quem o usa.

### A correção

```java
@Query("""
       SELECT p FROM Pedido p
         JOIN FETCH p.restaurante
         LEFT JOIN FETCH p.entregador
        WHERE p.cliente.id = :clienteId
        ORDER BY p.dataPedido DESC
       """)
List<Pedido> buscarHistoricoComRestauranteEEntregador(@Param("clienteId") Long clienteId);
```

**1 query**, e a resposta JSON é byte a byte idêntica à da versão com N+1 — conferido com `diff`.

**O `LEFT` do segundo fetch é obrigatório.** Pedido ainda não despachado tem `entregador_id` nulo, e
um `JOIN FETCH` comum é *inner*: os pedidos sem entregador **sumiriam** do histórico. Seria um bug
pior que o N+1, porque não aparece no log — aparece como pedido que o cliente jura ter feito.

**E não use `FetchType.EAGER` como atalho.** O mapeamento continua `LAZY`; quem decide o que trazer
junto é a consulta que precisa. `EAGER` resolveria aqui e criaria o mesmo problema em toda outra
consulta a `Pedido`.

## 4. `orphanRemoval`, na ordem certa

O método tira o item da coleção e não chama `delete()` em lugar nenhum:

```java
pedido.getItens().removeIf(item -> item.getId().equals(itemPedidoId));
pedido.recalcularValorTotal();
```

**Antes de declarar `orphanRemoval`** (só `cascade = ALL`):

```
ANTES  -> itens do pedido 1: 2 | valor_total: 133.70
DELETE -> HTTP 204
DEPOIS -> itens do pedido 1: 2 | valor_total: 117.70
```

A API respondeu 204. O `valor_total` **mudou** — o *dirty checking* pegou a alteração do campo e
gravou. E a linha de `item_pedido` **continua lá**.

Repare no que isso produz: o pedido 1 passou a valer R$ 117,70 enquanto a soma dos seus próprios
itens continua dando R$ 133,70. É exatamente o risco de divergência que a Etapa 1 assumiu ao
escolher `valor_total` como coluna materializada — e ele apareceu na primeira oportunidade.

O `CascadeType.REMOVE` não ajuda aqui: ele age quando o **pai** é deletado, e o pai está vivo.

**Depois de `orphanRemoval = true`:**

```
ANTES  -> itens do pedido 1: 2 | valor_total: 133.70
DELETE -> HTTP 204
DEPOIS -> itens do pedido 1: 1 | valor_total: 117.70
```

E no log aparece o `DELETE FROM item_pedido WHERE id = ?` que ninguém escreveu.

Use `orphanRemoval` quando o filho **não faz sentido sozinho** — a definição de entidade fraca da
Etapa 1. `ItemPedido` e `EnderecoEntrega` são os dois casos no JFood.

## 5. As quatro formas de consultar

| Forma | Endpoint | Queries |
|---|---|---|
| Derived query | `GET /api/v1/pedidos?status=A_CAMINHO` | 3 |
| JPQL com `JOIN` | `GET /api/v1/clientes/10/pedidos/por-categoria?categoria=Italiana` | 3 |
| Native query | `GET /api/v1/restaurantes/busca?termo=pizza` | 1 |
| Projection | `GET /api/v1/clientes/10/pedidos/resumo` | 1 |

**Derived query.** `findByStatusOrderByDataPedidoDesc` — nenhuma consulta escrita. As 3 queries são
1 pedido + seu restaurante + seu entregador: o mesmo N+1, agora em escala minúscula. É a prova de
que o problema não está no mapeamento, e sim em **qual consulta** você escolhe.

E o compilador não protege: `findByStattusOrderBy...`, com dois tês, compila normalmente — para o
`javac` é só um nome. O erro aparece na subida da aplicação.

**JPQL.** Navega o grafo de objetos: `JOIN p.restaurante r JOIN r.categoria c`. O `ON` de cada JOIN
é deduzido do mapeamento — não existe `ON r.id = p.restaurante_id` em lugar nenhum. As 3 queries são
1 (com o restaurante já vindo no fetch) + 2 entregadores, que ficaram lazy de propósito para deixar
a diferença visível.

**Native query.** O caso que **justifica** SQL puro: um recurso que só existe naquele banco.

```sql
SELECT * FROM restaurante r
 WHERE to_tsvector('portuguese', r.nome) @@ plainto_tsquery('portuguese', :termo)
```

Buscando por `pizza`, o resultado é:

```json
[{"id":1,"nome":"Pizzaria Bella Napoli","cep":"05407002"},
 {"id":2,"nome":"Forno & Pizza","cep":"05014001"}]
```

Os dois — inclusive o que tem a palavra **no meio** do nome. É exatamente o caso que a Etapa 2
mostrou não ter solução com índice B-tree: `LIKE 'Pizza%'` era rápido mas achava só o primeiro, e
`LIKE '%pizza%'` achava os dois varrendo 200 mil linhas. O full-text search dá os dois resultados
sem varrer a tabela.

E o parâmetro é nomeado, nunca concatenado. Com `:termo`, o Spring usa `PreparedStatement` e o
PostgreSQL trata o conteúdo como **dado**: quem digitar `'; DROP TABLE restaurante; --` no campo de
busca recebe zero resultados, não um banco destruído.

**Projection.** A tela de histórico mostra três campos, então a consulta traz três colunas:

```java
@Query("""
       SELECT p.id AS numero, r.nome AS restaurante, p.valorTotal AS valorTotal
         FROM Pedido p JOIN p.restaurante r
        WHERE p.cliente.id = :clienteId
        ORDER BY p.dataPedido DESC
       """)
List<ResumoPedido> listarResumoDoCliente(@Param("clienteId") Long clienteId);
```

**1 query**, e a entidade `Pedido` nem chega a ser instanciada.

**Os `AS` não são decoração.** O Spring casa cada alias com o nome do getter: `AS numero` alimenta
`getNumero()`. Sem os aliases, a projeção volta com todos os campos **nulos** — sem erro, sem
exceção, sem log. É um bug que se manifesta como dado vazio.

---

## Critério de pronto

Para cada endpoint está registrado acima quantas queries ele dispara e por quê:

| Endpoint | Queries | Justificativa |
|---|---|---|
| `/clientes/10/pedidos` | 6 | 1 + 2 restaurantes distintos + 3 entregadores distintos |
| `/clientes/10/pedidos-otimizado` | 1 | `JOIN FETCH` de restaurante e entregador |
| `/pedidos?status=A_CAMINHO` | 3 | 1 + o restaurante e o entregador do único pedido |
| `/clientes/10/pedidos/por-categoria` | 3 | 1 (restaurante no fetch) + 2 entregadores |
| `/restaurantes/busca` | 1 | Query nativa, sem relacionamento acessado |
| `/clientes/10/pedidos/resumo` | 1 | Projeção: três colunas, nenhuma entidade |
