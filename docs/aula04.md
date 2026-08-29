# Etapa 4 — Versionando o esquema com Flyway

Gabarito da Etapa 4 do JFood, correspondente à primeira metade da Aula 4. A partir daqui o esquema
do `jfood-db` **pertence ao Flyway**: nada é criado à mão, e nada existe só no banco de quem criou.

Todas as mensagens de erro desta página foram capturadas do log da aplicação.

## Como reproduzir

```bash
docker compose down -v && docker compose up -d
cd jfood-administrativo && ./mvnw spring-boot:run
```

Não há passo manual entre os dois comandos — esse é o critério de pronto da etapa.

---

## 1. Adotando o Flyway

Duas dependências, porque desde o Flyway 10 o suporte a cada banco vive em um módulo próprio: o
`flyway-core` sozinho reclama de dialeto não suportado.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

```properties
spring.jpa.hibernate.ddl-auto=none
spring.flyway.enabled=true
spring.flyway.validate-on-migrate=true
spring.flyway.out-of-order=false
spring.flyway.clean-disabled=true
```

`ddl-auto` sai de `validate` e vira `none`: o esquema tem um dono só. E `clean-disabled=true` é a
linha mais importante da lista — `flyway:clean` **apaga o esquema inteiro**, e essa propriedade
bloqueia a operação independentemente de quem a dispare.

A ordem é garantida pelo Spring Boot: a autoconfiguração roda o Flyway **antes** de inicializar o
`EntityManagerFactory`. Primeiro as migrações, depois o Hibernate olha para o resultado.

**Por que derrubar as tabelas da Etapa 2 só vale aqui.** O `V1` precisa encontrar um banco vazio, e
[`sql/aula04/00-derruba-tabelas-da-etapa-2.sql`](../sql/aula04) faz isso com `DROP TABLE ... CASCADE`.
Estamos descartando **dados de exercício, numa máquina local**. Adotar Flyway em um banco com dados
de verdade tem outro caminho: `spring.flyway.baseline-on-migrate=true`, ligado **uma única vez**,
que marca o estado atual como linha de base e passa a aplicar da `V2` em diante. Rodar o `DROP` em
produção é a diferença entre uma migração e um incidente.

## 2. A linha do tempo

| Migração | Conteúdo |
|---|---|
| `V1__schema_inicial.sql` | As 12 tabelas do JFood; as **FKs no fim**, por `ALTER TABLE` |
| `V2__add_taxa_entrega_pedido.sql` | Coluna `taxa_entrega`, nullable |
| `V3__seed_categorias_restaurante.sql` | Italiana, Japonesa, Brasileira, Árabe, Vegana |
| `V4__add_check_valor_pedido.sql` | `CHECK (valor_total >= 0)` e `CHECK (taxa_entrega >= 0)` |
| `V5__seed_dados_demo.sql` | Restaurantes, cardápios, clientes e 22 pedidos |
| `V6__reajuste_precos_cardapio.sql` | Reajuste de 8% em dois restaurantes |
| `V7__popula_taxa_entrega_pedidos_antigos.sql` | Preenche as linhas nulas com `0.00` |
| `V8__taxa_entrega_not_null.sql` | `SET NOT NULL` |
| `R__v_pedidos_do_dia.sql` | View do painel operacional |

**As FKs no fim.** Com todas as `FOREIGN KEY` declaradas por `ALTER TABLE` depois dos `CREATE
TABLE`, a ordem dos `CREATE` deixa de importar — e a migração fica legível de cima para baixo:
primeiro o que existe, depois como as coisas se ligam.

**Por que `taxa_entrega` nasce nullable.** Quando essa coluna é adicionada, `pedido` já tem linhas.
`NOT NULL` sem `DEFAULT` seria recusado na hora; e um `DEFAULT 0.00` seria uma **afirmação sobre o
passado** — dizer que todo pedido antigo teve frete grátis. Alguém precisa assinar isso. A `V5`
inclui dois pedidos "importados do sistema legado" justamente com `taxa_entrega` nula, para que os
passos seguintes tenham trabalho de verdade a fazer.

Saída de uma migração completa a partir do banco vazio:

```
Migrating schema "public" to version "1 - schema inicial"
...
Migrating schema "public" with repeatable migration "v pedidos do dia"
Successfully applied 9 migrations to schema "public", now at version v8 (execution time 00:00.071s)
```

### O efeito colateral que a V6 **não** tem

A `V6` reajusta o cardápio em 8%. Depois dela:

```
       nome        | preco_hoje | preco_no_pedido_1
-------------------+------------+-------------------
 Refrigerante Lata |       8.64 |              8.00
 Pizza Margherita  |      59.29 |             54.90
```

O cardápio subiu; o pedido 1 continua valendo o que foi cobrado. É a decisão da Etapa 1 — copiar o
preço para `item_pedido` — funcionando três etapas depois, sem que ninguém precise se lembrar dela.

## 3. `taxa_entrega` obrigatória, em três passos

| Passo | Migração | Comando |
|---|---|---|
| 1 | `V2` | `ADD COLUMN taxa_entrega NUMERIC(10,2)` |
| 2 | `V7` | `UPDATE pedido SET taxa_entrega = 0.00 WHERE taxa_entrega IS NULL` |
| 3 | `V8` | `ALTER COLUMN taxa_entrega SET NOT NULL` |

**A `V2` não foi editada** — e não poderia ser. Ela já rodou em algum ambiente; o Flyway guarda o
checksum dela.

**Por que três migrações e não uma.** Entre a `V2` e a `V8`, a aplicação continuou rodando em
produção. Se o `SET NOT NULL` viesse no mesmo script que o `UPDATE`, a janela entre os dois comandos
— ainda que de milissegundos — bastaria para um `INSERT` sem taxa entrar e derrubar a migração
inteira, no meio de um deploy.

Resultado: 22 pedidos, 22 com taxa preenchida.

## 4. A quebra de checksum

Um único espaço em branco acrescentado à `V3` já aplicada. A aplicação **não sobe**:

```
Error creating bean with name 'flywayInitializer' ...: Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 3
-> Applied to database : -1406117604
-> Resolved locally    : 1097102898
Either revert the changes to the migration, or run repair to update the schema history.
```

Repare em três coisas:

1. **A falha é na subida**, não em runtime. O Flyway roda antes do `EntityManagerFactory`, então a
   aplicação inteira é cancelada — é o comportamento desejado: melhor não subir do que subir com o
   esquema divergente do que o código espera.
2. **Um espaço em branco basta.** O checksum é do arquivo, não do SQL semântico. Reformatar um
   script já aplicado quebra o build.
3. **A mensagem oferece `repair`** — e é aí que mora a armadilha. `flyway:repair` **não conserta o
   banco**: ele reescreve o histórico para dizer que o arquivo atual é o que rodou. Se você editou o
   conteúdo de verdade, o `repair` apenas apaga a evidência de que o banco e o script discordam.

**A correção certa é a `V6`.** Desfiz a edição na `V3` e criei uma migração nova com o `UPDATE` que
eu queria fazer. E o exercício esconde uma verdade: **um reajuste não é correção do passado**, é um
fato de negócio que aconteceu depois — e fatos de negócio merecem o próprio ponto na linha do tempo.
Editar a `V3` teria apagado do histórico que os preços um dia foram outros.

## 5. A migração repetível

`R__v_pedidos_do_dia.sql` cria a view do painel operacional com `CREATE OR REPLACE VIEW` — sem o
`OR REPLACE`, a segunda execução falharia porque o objeto já existe. O princípio é **idempotência**.

Editei a view acrescentando a coluna `metodo_pagamento` e subi de novo. O log:

```
Migrating schema "public" with repeatable migration "v pedidos do dia"
Successfully applied 1 migration to schema "public"
```

E o `flyway_schema_history` mostra o que foi reaplicado e o que não foi:

```
 installed_rank | version |             description             |  installed_on
----------------+---------+-------------------------------------+-----------------
              1 | 1       | schema inicial                      | 12:36:46.902205
              ...
              8 | 8       | taxa entrega not null               | 12:36:47.025991
              9 |         | v pedidos do dia                    | 12:36:47.032161
             10 |         | v pedidos do dia                    | 12:37:38.996107
```

A linha 10 é a reaplicação, quase um minuto depois. As `V__` continuam com o carimbo de tempo
original: elas rodam **uma vez**, para sempre.

É por isso que view, função e trigger pertencem a `R__`: elas mudam com frequência, e uma linha do
tempo com `V11`, `V12`, `V13` da mesma view é um cemitério de versões do mesmo objeto.

## 6. A colisão de versões entre branches

Simulada de verdade: duas branches saindo da `aula04`, cada uma com sua `V9`.

```bash
git checkout -b colisao-a aula04    # V9__add_tempo_preparo_restaurante.sql
git checkout -b colisao-b aula04    # V9__add_nota_media_entregador.sql
git checkout colisao-a && git merge colisao-b
```

**O merge passa sem conflito.** É esse o ponto: são dois arquivos com nomes diferentes, e para o Git
não há nada de errado — ele apenas acrescenta o arquivo novo.

O problema só aparece na subida:

```
Found more than one migration with version 9
Offenders:
-> .../db/migration/V9__add_tempo_preparo_restaurante.sql (SQL)
-> .../db/migration/V9__add_nota_media_entregador.sql (SQL)
```

Um erro que o Git não pega, o code review não pega e o teste unitário não pega — e que quebra o
deploy de quem fizer o merge.

### A convenção que o time do JFood adota

1. **A versão é escolhida no momento do merge, não no momento de escrever.** Antes de abrir o PR,
   quem for mesclar renomeia sua migração para o próximo número livre em `main`. Renomear é seguro
   **enquanto o script não rodou em nenhum ambiente compartilhado**.
2. **Migração nunca vai para uma branch de vida longa.** Quanto mais tempo o script fica fora da
   `main`, maior a chance de outra pessoa ocupar o número.
3. **`out-of-order` fica `false`.** Ligar essa opção faria a `V9` atrasada rodar depois da `V10` já
   aplicada, e a ordem passaria a depender de quem fez o deploy primeiro. Preferimos o erro.
4. **Nada de numeração por data (`V20260829120000__`).** Ela elimina a colisão e cria outra coisa
   pior: ordem que ninguém consegue ler, e um histórico em que não dá para saber de cabeça o que veio
   antes.

---

## Critério de pronto

```
docker compose down -v && docker compose up -d
cd jfood-administrativo && ./mvnw spring-boot:run
```

```
Successfully applied 9 migrations to schema "public", now at version v8
Started JfoodAdministrativoApplication in 2.312 seconds

 usuarios | restaurantes | pedidos | itens | pedidos_hoje
----------+--------------+---------+-------+--------------
       19 |            7 |      22 |    36 |            3
```

O banco inteiro reconstruído do zero, sem nenhum passo manual — e os endpoints da Etapa 3
respondendo em cima dele.
