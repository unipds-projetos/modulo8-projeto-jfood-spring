# Etapa 8 — Catálogo de restaurantes e cardápios no MongoDB

Gabarito da Etapa 8 do JFood, correspondente à Aula 8. O serviço é o
[`jfood-catalogo`](../jfood-catalogo), na porta **8082**.

## Como reproduzir

```bash
docker compose up -d

docker cp mongo/seed-catalogo.js jfood-mongodb:/tmp/seed.js
docker exec jfood-mongodb mongosh --quiet -u admin -p adminpassword \
  --authenticationDatabase admin jfood_catalogo --file /tmp/seed.js

cd jfood-catalogo && ./mvnw spring-boot:run
```

As requisições estão em [`postman/jfood-catalogo.postman_collection.json`](../postman).

---

## 1. Por que o cardápio sai do PostgreSQL — e por que pedido fica

O critério não é "documento é mais flexível". São dois números.

**Write amplification.** A alternativa relacional honesta seria uma coluna `JSONB` com o cardápio
inteiro. O problema é como o PostgreSQL grava: ele é MVCC, e **todo `UPDATE` reescreve a linha
inteira** — mais o TOAST, quando o valor passa de ~2 KB, que é sempre o caso de um cardápio. Trocar
o preço de **um** item de um restaurante com 80 itens reescreve os 80, e o WAL carrega o documento
completo em cada alteração de preço. Em um delivery, alteração de preço não é evento raro: é
terça-feira.

No MongoDB, `$set` em `cardapio.2.itens.5.preco` altera aquele campo. A conta de escrita é
proporcional ao que mudou, não ao tamanho do documento.

**Sharding nativo.** O catálogo cresce com o número de restaurantes — dezenas de milhares, com
crescimento geográfico. Quando não couber em um nó, o MongoDB fatia por shard key e continua. No
PostgreSQL, particionar `JSONB` por região é trabalho manual, com roteamento na aplicação e sem
rebalanceamento automático.

**Por que `usuario`, `pedido` e `pagamento` FICAM no PostgreSQL.** Os mesmos dois critérios,
invertidos:

- **Write amplification não existe ali.** As linhas são pequenas e de tamanho fixo — um `UPDATE` de
  `status` reescreve algumas centenas de bytes. E cada pedido é escrito uma vez e alterado poucas
  vezes.
- **Sharding não é necessário.** A Etapa 7 mediu: **514 linhas/s no pico**, 3% a 10% de um único nó.
  O subsistema transacional não chega perto de precisar sair de uma máquina — e sair dela custaria
  exatamente o que ele não pode perder: transação multi-tabela, FK e `SELECT ... FOR UPDATE`, que
  são as três coisas em que as Etapas 5 e 6 se apoiaram.

## 2. O documento `restaurantes`

Um documento por restaurante, com o cardápio inteiro embutido em seções, e cada item com sua lista
de **opcionais**. É a parte que o relacional não modela bem:

```javascript
opcionais: [
  { nome: "Tamanho", obrigatorio: true, minimo_escolhas: 1, maximo_escolhas: 1,
    escolhas: [ { nome: "Broto", acrescimo: 0 }, { nome: "Media", acrescimo: 8 }, ... ] },
  { nome: "Borda recheada", obrigatorio: false, minimo_escolhas: 0, maximo_escolhas: 1, ... }
]
```

"Tamanho" tem 3 escolhas e é obrigatório; "ponto da carne" tem 3 e só aparece em carne;
"acompanhamentos" permite escolher até 3. Cada restaurante inventa os seus. Em tabela, isso vira EAV
ou quarenta colunas nulas.

**Navegando por notação de ponto no `mongosh`** — restaurantes que têm algum item vegano:

```javascript
db.restaurantes.find({ "cardapio.itens.tags": "vegano" })
```

O caminho atravessa dois níveis de array sem `$unwind` e sem `$elemMatch` — o MongoDB aplica a
condição a qualquer elemento no caminho. Resultado no seed: **4 restaurantes** (Bella Napoli, Sabor
da Roça, Verde & Cia e Forno & Pizza).

Pelo endpoint:

```
GET /api/v1/restaurantes/com-item-tag?tag=vegano
  Pizzaria Bella Napoli
  Sabor da Roça
  Verde & Cia
  Forno & Pizza
```

## 3. CRUD com Spring Data MongoDB

`@Document(collection = "restaurantes")` na raiz, `@Id String id` (o `ObjectId`, não o `BIGSERIAL`
do JPA), e os subdocumentos — `SecaoCardapio`, `ItemResumo`, `Opcional` — como **records puros**, sem
`@Document` e sem `@Id`. Endpoints de listagem, detalhe, criação, atualização e exclusão.

**`@Field` explícito em todos os campos**, mesmo quando o nome coincide. Sem ele, o nome da
propriedade Java vira o nome do campo BSON — e um refactor inocente de `notaMedia` para `nota` faz a
aplicação parar de enxergar dados que continuam lá, sem erro nenhum. O mapeamento passa a ser uma
decisão explícita, versionada junto com o código.

### Três armadilhas que este gabarito encontrou

**(a) `spring.data.mongodb.uri` está deprecada no Spring Boot 4.** A propriedade agora é
`spring.mongodb.uri`. Com a antiga, `spring.data.mongodb.uri`, a aplicação **sobe normalmente** e só
quebra na primeira operação:

```
Command execution failed on MongoDB server with error 13 (Unauthorized):
'Command createIndexes requires authentication'
```

O `authSource=admin` continua obrigatório: o usuário `admin` foi criado no banco `admin` (pelo
`MONGO_INITDB_ROOT_*`), e não em `jfood_catalogo`.

**(b) O BSON não tem tipo "hora do dia".** `abre_as: "18:00"` não é um instante — é um horário que se
repete todo dia. Ele tem `Date` (instante) e `Timestamp` (interno do replica set), e nenhum dos dois
serve. Guardar como string ISO e converter nas duas pontas resolve; sem o converter, a leitura falha
com `ConverterNotFoundException: No converter found capable of converting from type
[java.lang.String] to type [java.time.LocalTime]`. Está em
[`MongoConversoesConfig`](../jfood-catalogo/src/main/java/br/com/unipds/jfood/catalogo/config/MongoConversoesConfig.java).

**(c) `String` no Java não casa com `ObjectId` no BSON.** Uma referência declarada como
`String restauranteId` é gravada e **consultada** como string. O `updateMulti` do Subset Pattern
procurava `"6a93..."` e o documento tinha `ObjectId("6a93...")`:

```
Subset propagado para 0 itens do restaurante 6a9303dda6d8524125d1a7bb
```

Zero documentos, zero erros, zero logs de alerta. A correção é declarar o tipo alvo:

```java
@Field(value = "restaurante_id", targetType = FieldType.OBJECT_ID) String restauranteId
```

Depois disso, `Subset propagado para 4 itens`. É o tipo de bug que só aparece em produção, semanas
depois, como "o nome do restaurante está errado na busca".

## 4. Paginação obrigatória

```java
@GetMapping
public ResponseEntity<Page<Restaurante>> listar(
        @PageableDefault(size = 20, sort = "notaMedia", direction = Direction.DESC)
        Pageable pageable) { ... }
```

O `sort` não é detalhe técnico: **a tela inicial de um delivery mostra os mais bem avaliados**, e não
a ordem de inserção. O `@PageableDefault` é onde essa decisão de produto vira código — e onde ela
fica visível para quem for revisar.

```
GET /api/v1/restaurantes?page=0&size=3
  Sushi Yuki    4.9  Japonesa   19:00 - 23:00
  Verde & Cia   4.8  Vegana     11:30 - 21:00
  Cantina do Zé 4.7  Italiana   18:00 - 23:30
  page: {'size': 3, 'number': 0, 'totalElements': 7, 'totalPages': 3}
```

O envelope `"page": {...}` é o `PagedModel`, e não o `PageImpl`. Sem
`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`, o Spring emite:

```
Serializing PageImpl instances as-is is not supported, meaning that there is no
guarantee about the stability of the resulting JSON structure!
```

`PageImpl` é uma classe **interna** do Spring. Se a equipe renomear um campo privado dela na próxima
versão, o JSON da sua API muda e todos os apps mobile e front-ends quebram — por causa de uma
atualização de dependência.

## 5. Índices com critério, medidos

Indexados: `nome` (busca) e `(categoria, nota_media)` (filtro + ordenação da tela inicial). **Fora**
do índice, de propósito: `descricao`, `foto_url`, `opcionais`, `abre_as` e `fecha_as` — ninguém
filtra por eles, e todo índice é escrita a mais em todo `insert`.

`mongo/explain-indices.js` carrega 100 mil restaurantes sintéticos, mede as duas situações e apaga
tudo:

| | Sem índice | Com índice |
|---|---|---|
| Estágio vencedor | **COLLSCAN** | **FETCH** (sobre `IXSCAN`) |
| Documentos examinados | **100.007** | **1** |
| Chaves de índice examinadas | 0 | 1 |
| Documentos retornados | 1 | 1 |
| Tempo | **34 ms** | **0 ms** |

Cem mil documentos lidos para devolver um. Repare no par que importa mais que o tempo:
`totalDocsExamined` **100.007** contra `nReturned` **1**. Essa razão é o diagnóstico — o tempo varia
com a máquina, a razão não.

O *write penalty*, no tamanho dos índices da coleção com 100 mil documentos:

```
_id_:                           20 KB
idx_restaurante_categoria_nota: 292 KB
idx_restaurante_nome:         1.164 KB
```

Cada um deles é uma estrutura a mais para manter em **toda** escrita.

### Por que `auto-index-creation=true` não vai para produção

Com ele ligado, **cada instância** da aplicação tenta criar os índices na subida. Em uma coleção
grande isso é uma operação de minutos que **bloqueia escrita** — disparada por um deploy, sem
ninguém decidir, em horário que ninguém escolheu. E há um agravante: a criação vira efeito colateral
de uma anotação em uma classe de domínio, e some da revisão de código. O índice mais caro do sistema
passa a ser adicionado por quem estava mexendo no mapeamento.

Em produção o índice é criado com `createIndex({...}, {background: true})` por quem opera, em janela
combinada. Aqui o meio-termo é o
[`MongoIndexInitializer`](../jfood-catalogo/src/main/java/br/com/unipds/jfood/catalogo/repository/MongoIndexInitializer.java):
criação **explícita**, versionada e visível no log.

## 6. A coleção `itens_cardapio` e o Subset Pattern

O JFood lançou a busca global: *"quero pizza de calabresa perto de mim"* — uma busca por **item**, e
não por restaurante. Com os itens só embutidos, respondê-la exigiria varrer todos os restaurantes e
abrir cada cardápio. É a mesma quebra que "Minha Playlist" causou no Javify.

A solução tem três partes:

1. **A coleção `itens_cardapio`**, com o item completo (preço, foto, tags, opcionais);
2. **O subset do restaurante duplicado dentro dele** — `_id`, nome, foto, nota e taxa de entrega:
   exatamente os campos que o card do resultado mostra, e nenhum a mais;
3. **O resumo do item mantido no restaurante**, agora com `item_id`, fechando os dois sentidos.

```
GET /api/v1/restaurantes/itens/busca?termo=pizza
  Pizza Calabresa       R$ 53.89 | Cantina do Zé (nota 4.7, taxa 7.9)
  Pizza Margherita      R$ 59.29 | Cantina do Zé (nota 4.7, taxa 7.9)
  Pizza Marguerita Zero R$ 56.90 | Forno & Pizza (nota 4.1, taxa 7.5)
  Pizza Portuguesa      R$ 57.13 | Pizzaria Bella Napoli (nota 4.5, taxa 6.9)
  Pizza Quatro Queijos  R$ 64.69 | Pizzaria Bella Napoli (nota 4.5, taxa 6.9)
  Pizza Vegetariana     R$ 51.90 | Forno & Pizza (nota 4.1, taxa 7.5)
```

Nome do item, preço, **e** nome, nota e taxa do restaurante — em **uma** consulta a **uma** coleção.
Sem o subset, seriam seis idas a `restaurantes` para montar seis cards.

### O preço da duplicação: o `updateMany`

```java
mongoTemplate.updateMulti(
        Query.query(Criteria.where("restaurante.restauranteId").is(restaurante.id())),
        new Update().set("restaurante.nome", restaurante.nome())
                    .set("restaurante.fotoUrl", restaurante.fotoUrl())
                    .set("restaurante.notaMedia", restaurante.notaMedia())
                    .set("restaurante.taxaEntrega", restaurante.taxaEntrega()),
        ItemCardapio.class);
```

Trocando o nome do restaurante pela API:

```
subset ANTES:  Cantina do Zé | Cantina do Zé | Cantina do Zé | Cantina do Zé
PUT /api/v1/restaurantes/{id} -> 200
Subset propagado para 4 itens do restaurante 6a9303dda6d8524125d1a7bb
subset DEPOIS: Cantina do Zé — Trattoria | ... (4 itens)
busca por item já mostra o nome novo
```

O custo é proporcional ao número de itens **daquele** restaurante — dezenas, não milhões.

### E se a taxa de entrega mudar todo dia?

**Ela sai do subset.** E a razão é aritmética, não estética.

O subset é uma aposta: *este dado muda com uma frequência muito menor do que é lido*. Nome e foto de
restaurante mudam algumas vezes por ano; a nota média varia devagar e ninguém percebe alguns minutos
de defasagem. A duplicação se paga.

Taxa de entrega com **precificação dinâmica** quebra a aposta em dois lugares:

- **Custo de escrita.** Um `updateMany` por restaurante por reajuste. Com 50 mil restaurantes, 80
  itens cada e reprecificação de hora em hora, são 4 milhões de documentos reescritos por hora — só
  para manter uma cópia atualizada.
- **Correção.** Precificação dinâmica depende de **demanda e distância**, e distância depende do
  endereço de quem está buscando. O valor deixa de ser uma propriedade do restaurante e passa a ser
  uma propriedade **do par (restaurante, cliente, momento)**. Nenhuma duplicação estática representa
  isso — o subset ficaria *errado*, e não apenas velho.

**O que fazer:** manter no subset o que é estável (`_id`, nome, foto, nota) e calcular a taxa no
momento de montar a tela, para os poucos resultados que a página mostra — em memória, ou por uma
chamada ao serviço de precificação. É o mesmo raciocínio que a Etapa 6 aplicou a `valor_total`:
**dado derivado só vale a pena materializar quando o custo de mantê-lo é menor que o de recalculá-lo.**

## 7. A aggregation pipeline

*"Qual o preço médio e a quantidade de itens por categoria de cozinha, considerando apenas
restaurantes abertos?"*

```java
newAggregation(
    match(Criteria.where("aberto").is(true)),   // <- ANTES do unwind
    unwind("cardapio"),
    unwind("cardapio.itens"),
    group("categoria").count().as("totalItens")
                      .avg("cardapio.itens.preco").as("precoMedio")
                      ...);
```

```
GET /api/v1/admin/relatorios/preco-por-categoria
  Italiana    itens= 7  medio=R$ 45.30  min=6.48  max=66.96  restaurantes abertos=2
  Árabe       itens= 4  medio=R$ 14.47  min=7.50  max=32.00  restaurantes abertos=1
  Brasileira  itens= 3  medio=R$ 45.50  min=12.00 max=78.00  restaurantes abertos=1
  Japonesa    itens= 3  medio=R$ 51.57  min=29.90 max=89.90  restaurantes abertos=1
  Vegana      itens= 2  medio=R$ 40.95  min=39.90 max=42.00  restaurantes abertos=1
```

Confere: "Forno & Pizza" está `aberto: false`, e os seus 2 itens ficaram de fora — a categoria
Italiana mostra 7 itens (Cantina 4 + Bella Napoli 3) e 2 restaurantes abertos.

**A ordem dos estágios é a decisão de performance da pipeline.** O `$match` vem **antes** dos dois
`$unwind`, por dois motivos:

1. **`$unwind` multiplica documentos.** Cada restaurante vira uma linha por seção e depois uma por
   item. Filtrar depois é descartar documentos que você mesmo acabou de fabricar.
2. **Só o `$match` inicial usa índice.** Depois do primeiro `$unwind`, os documentos são sintéticos —
   não existem na coleção, e nenhum índice os cobre. O filtro vira varredura em memória.

---

## Critério de pronto

As duas telas principais, cada uma atendida por **uma única consulta a um único documento**:

| Tela | Consulta | Documento |
|---|---|---|
| Detalhe do restaurante | `db.restaurantes.findOne({_id})` | 1 documento com o cardápio inteiro, seções, itens e opcionais |
| Resultado da busca por item | `db.itens_cardapio.find({nome: /.../})` | 1 documento por card, já com o subset do restaurante |
