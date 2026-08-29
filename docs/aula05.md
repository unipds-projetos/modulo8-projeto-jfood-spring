# Etapa 5 — Concorrência no fluxo do pedido

Gabarito da Etapa 5 do JFood, correspondente à Aula 5. Delivery é um domínio cheio de disputa: um
cupom com estoque, um entregador que só pega uma corrida por vez, um cliente que clica duas vezes em
"Confirmar".

Todos os resultados desta página foram medidos com a aplicação de pé contra o `jfood-db`.

## Como reproduzir

```bash
docker compose down -v && docker compose up -d
cd jfood-administrativo && ./mvnw spring-boot:run
```

As requisições estão na pasta "Aula 5" de
[`postman/jfood-administrativo.postman_collection.json`](../postman). Os cenários concorrentes usam
`curl` em paralelo — cada bloco de comandos está junto do resultado, abaixo.

---

## 1. O duplo pagamento, reproduzido

[`sql/aula05/duplo-pagamento.sql`](../sql/aula05) tem os quatro blocos para rodar intercalados em
duas abas do DBeaver. Os dois `SELECT` leem `CRIADO`, os dois `UPDATE` passam sem erro.

O `READ COMMITTED` do PostgreSQL fez o trabalho dele: nenhuma das duas transações leu dado sujo. O
problema não é isolamento — é a **janela** entre ler o status e gravar a mudança. É onde o *lost
update* nasce, e fechá-la é o assunto do resto da etapa.

> No JFood, o `UNIQUE` em `pagamento.pedido_id` mascara metade do estrago: o segundo `INSERT` falha.
> Tire o `UNIQUE` mentalmente e você tem dois pagamentos para o mesmo pedido. A constraint é uma
> rede de segurança — não é o controle de concorrência.

## 2. Confirmação com lock pessimista

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
@Query("SELECT p FROM Pedido p WHERE p.id = :id")
Optional<Pedido> buscarParaAtualizacao(@Param("id") Long id);
```

A ordem no serviço é a aula inteira: **1.** adquire o lock → **2.** só então verifica o status →
**3.** grava o novo status e o pagamento, na mesma transação.

Seis confirmações simultâneas do mesmo pedido:

```bash
for i in 1 2 3 4 5 6; do
  curl -s -o /dev/null -w "%{http_code} " -X POST localhost:8080/api/v1/pedidos/18/confirmar &
done; wait
```

```
409 409 409 409 409 200
status do pedido 18: CONFIRMADO | pagamentos: 1
```

Uma vence, cinco recebem 409. A thread que chega durante o processamento trava no passo 1; quando a
primeira commita, ela acorda, lê `CONFIRMADO`, cai no `if` e recebe o erro. **Zero duplo pagamento.**
O custo é a espera.

**O `lock.timeout` não é opcional.** No log, o Hibernate emite `set local lock_timeout = 3000` antes
do `SELECT ... FOR UPDATE` e restaura o valor original depois. Sem ele, uma transação travada —
alguém que abriu um `BEGIN` no DBeaver e foi almoçar — bloqueia **todas** as confirmações daquele
pedido indefinidamente, e o sintoma que chega ao suporte é "o app parou". Com 3 s, quem espera demais
recebe um erro e a fila anda.

## 3. Fila de despacho com `SKIP LOCKED`

A migração `V9` cria `pedido.despachado_em` e um índice **parcial**:

```sql
CREATE INDEX idx_pedido_fila_despacho
    ON pedido (data_pedido)
 WHERE status = 'CONFIRMADO' AND despachado_em IS NULL;
```

O `WHERE` na definição faz o índice cobrir só as linhas que o job consulta. Com 22 pedidos, o
`EXPLAIN` continua mostrando `Seq Scan` — e isso é o planejador acertando: varrer 22 linhas é mais
barato que abrir um índice. Em produção, com milhões de pedidos `ENTREGUE` e algumas centenas
`CONFIRMADO`, a diferença é entre um índice de kilobytes e um de gigabytes.

A query:

```sql
SELECT * FROM pedido
 WHERE status = 'CONFIRMADO' AND despachado_em IS NULL
 ORDER BY data_pedido
 LIMIT :limite
   FOR UPDATE SKIP LOCKED
```

Aqui **não** se usa `@Lock`: o `FOR UPDATE` já está explícito no SQL nativo, e anotar por cima só
criaria conflito.

Duas rodadas simultâneas, com 22 pedidos na fila e lote de 20:

```
lote A: [15, 11]
lote B: [21, 22, 1, 2, 12, 3, 7, 16, 8, 4, 19, 13, 17, 9, 14, 10, 5, 6, 20, 18]
A=2, B=20, intersecao=[], uniao=22
despachados: 22 / 22
```

**Interseção vazia.** Nenhuma das duas esperou pela outra, nenhum pedido foi despachado duas vezes,
e os 22 foram processados. É esse padrão que substitui um sistema de filas externo quando o banco já
é a fonte da verdade dos dados.

## 4. Locking otimista no cancelamento

`V10` acrescenta `versao BIGINT NOT NULL DEFAULT 0`, e a entidade ganha `@Version`. O `DEFAULT 0` é
obrigatório: as 22 linhas que já existem precisam de valor inicial, senão o primeiro
`UPDATE ... WHERE versao = ?` não encontra nada e o Hibernate acusa conflito onde não há.

Três cancelamentos simultâneos do pedido 20 (`CONFIRMADO`, `versao = 1`):

```
resposta 1: HTTP/1.1 204
resposta 2: HTTP/1.1 409  {"erro":"Conflito de concorrencia detectado.", ...}
resposta 3: HTTP/1.1 409  {"erro":"Conflito de concorrencia detectado.", ...}
pedido 20 depois: status=CANCELADO versao=2
```

Ninguém adquiriu lock. O conflito foi detectado **na escrita**: o `UPDATE ... WHERE id = ? AND versao
= 1` das duas perdedoras afetou zero linhas, e o Hibernate lançou
`OptimisticLockingFailureException`.

### As duas formas de tratar o mesmo conflito

**HTTP 409, para quem está na frente da tela.** É a única resposta honesta: o servidor não pode
decidir sozinho qual das duas edições vale.

**Retry com backoff, para quem não está.** No job de background que recalcula o valor do pedido,
ninguém espera resposta — então a resposta certa é tentar de novo:

```java
@Retryable(includes = OptimisticLockingFailureException.class,
           maxRetries = 3, delay = 100, multiplier = 2.0, jitter = 50)
@Transactional
public void recalcularValorTotal(Long pedidoId) { ... }
```

> **Nota de versão.** Materiais mais antigos usam o `@Retryable` do projeto `spring-retry`, com
> `retryFor`, `maxAttempts` e `@Backoff` — ele **não está no BOM do Spring Boot 4** e, sem versão
> explícita, o build falha com `'dependencies.dependency.version' ... is missing`. No Spring Boot 4 /
> Spring Framework 7 o retry nasceu no **núcleo**
> (`org.springframework.resilience.annotation`), com `includes`, `maxRetries`, `delay`,
> `multiplier` e `jitter` na própria anotação, ligado por `@EnableResilientMethods`. É a mesma ideia,
> sem dependência externa — e o `spring-boot-starter-aop` virou `spring-boot-starter-aspectj`, que
> nem é necessário aqui.

O `jitter = 50` espalha as tentativas para que três threads que colidiram não voltem a colidir
exatamente 100 ms depois — é o mesmo raciocínio do jitter contra o *cache stampede* da Aula 9.

## 5. O cupom: pessimista, e por quê

`V11` cria `cupom` e `cupom_resgate`, com duas invariantes no próprio banco:
`CHECK (quantidade_resgatada <= quantidade_total)` e `UNIQUE (cupom_id, cliente_id)`.

**Dez clientes disputando `ALMOCO10` (100 unidades), simultaneamente:**

```
200 200 200 200 200 200 200 200 200 200
resgatada=10 | linhas em cupom_resgate=10
o mesmo cliente de novo: 409
```

O contador ficou **exatamente** em 10 — nenhum incremento se perdeu.

**Dez clientes disputando `ESCASSO3` (3 unidades), simultaneamente:**

```
200 200 200 409 409 409 409 409 409 409
resgatada=3 de 3
```

Três vencem, sete recebem 409. O cupom **nunca** é vendido além do estoque.

### A justificativa por escrito

O critério da aula é **contenção**: se dois usuários disputam a mesma linha com frequência, use
pessimista; se a disputa é rara, use otimista.

O cenário do enunciado — 100 unidades para 50 mil clientes, no minuto do lançamento — é o extremo da
escala. **Todo mundo disputa a mesma linha, ao mesmo tempo.** A contenção não é alta: é praticamente
100%.

Com controle otimista, cada uma das 50 mil requisições leria a versão atual, tentaria escrever e
quase todas falhariam — e cada falha viraria um retry, que voltaria a colidir. O sistema gastaria a
maior parte do trabalho **refazendo trabalho jogado fora**, e a taxa de sucesso por rodada cairia à
medida que a carga subisse. É o padrão que não converge sob pressão, exatamente quando a pressão é
máxima.

Com controle pessimista, as requisições **enfileiram**. Cada uma espera a anterior, lê o estoque já
atualizado e decide certo. O throughput é limitado pela duração da transação — que aqui é curtíssima,
um `UPDATE` de contador — e a espera é previsível, com o `lock_timeout` de 3 s cortando quem esperou
demais. É a escolha certa **para este caso**, e seria a errada para a edição de perfil.

> As duas defesas do banco continuam valendo. O `CHECK` impede estoque negativo mesmo que a aplicação
> erre; o `UNIQUE (cupom_id, cliente_id)` derruba o segundo resgate do mesmo cliente mesmo que dois
> requests dele passem pelo lock. Controle de concorrência na aplicação **e** invariante no banco não
> são redundância: são camadas diferentes.

## 6. `REQUIRES_NEW`: o log que sobrevive ao rollback

O gateway simulado permite programar uma recusa (`POST /api/v1/gateway/recusar/{id}`) para provocar
o cenário sem esperar o azar.

```
antes:  status=CRIADO pagamentos=0 logs=0
POST /gateway/recusar/19  -> 202
POST /pedidos/19/confirmar -> HTTP/1.1 402
depois: status=CRIADO pagamentos=0 logs=1
conteudo do log: Operadora recusou a cobranca de R$ 104.70 via PIX
```

A transação principal fez rollback: o pedido voltou a `CRIADO` e nenhum pagamento foi gravado. E o
log **ficou** — porque `LogPagamentoService.registrarFalha` roda em
`@Transactional(propagation = REQUIRES_NEW)`, que suspende a transação de fora, abre a sua, commita e
só então devolve o controle.

Sem `REQUIRES_NEW`, o rollback externo levaria o log junto — e a rastreabilidade do incidente se
perderia justamente no caso em que ela é necessária.

> **O custo:** `REQUIRES_NEW` consome uma **segunda conexão** do pool enquanto a primeira fica
> suspensa. Usá-lo dentro de um laço sobre muitos itens esgota o pool e trava a aplicação.

A tabela `log_pagamento` também não tem FK para `pedido`, de propósito: uma trilha de auditoria que
pode ser derrubada pela integridade referencial daquilo que ela audita não é uma trilha.

## 7. A armadilha do *self-invocation*

`AutoInvocacaoService` está no código de propósito:

```java
public void confirmarComNotificacao(Long pedidoId) {
    this.confirmar(pedidoId);      // <- chamada direta: bypassa o proxy AOP
    notificarCliente(pedidoId);
}

@Transactional                     // IGNORADO na chamada acima
public void confirmar(Long pedidoId) { ... }
```

```
antes:  status=CRIADO pagamentos=0
POST /pedidos/17/confirmar-errado: 204
depois: status=CRIADO pagamentos=1
```

**Leia de novo: `204`.** A API respondeu sucesso. E o banco ficou com um **pagamento registrado para
um pedido que continua `CRIADO`** — o cliente foi cobrado por um pedido que o sistema acha que nunca
foi confirmado.

O mecanismo, linha a linha:

1. Sem transação, o `Pedido` volta **detached** do repositório. O `setStatus(CONFIRMADO)` altera
   memória e nada mais: nenhum *dirty checking* vai rodar.
2. `pagamentoRepository.save(...)` **tem** transação própria — a do `SimpleJpaRepository`. Essa
   linha grava.

Metade da operação persiste. Nenhum erro, nenhum log, nenhum teste de unidade reprovando.

### Um detalhe que vale a nota

O método errado usa `findById`, e não o `buscarParaAtualizacao` com lock pessimista. Não é descuido.
Com o lock, o Hibernate percebe que não há transação e explode na primeira linha:

```
jakarta.persistence.TransactionRequiredException: No active transaction
```

Esse é o caso **feliz** — quebra alto, na primeira execução, e alguém conserta. A versão sem lock não
quebra: ela responde 204 e corrompe os dados devagar. É essa que chega em produção.

### A correção

Extrair para outro bean — `ConfirmacaoPedidoService` — e chamar por injeção. A chamada passa pelo
proxy e o `@Transactional` é aplicado:

```
POST /pedidos/17/confirmar: 200
depois: status=CONFIRMADO pagamentos=1
```

O mesmo vale para `@Async`, `@Cacheable` e `@Retryable` — **toda** anotação baseada em proxy.

---

## Critério de pronto

O teste [`ConfirmacaoConcorrenteTest`](../jfood-administrativo/src/test/java/br/com/unipds/jfood/administrativo/ConfirmacaoConcorrenteTest.java)
dispara 8 threads confirmando o mesmo pedido a partir de uma `CountDownLatch` comum:

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

As asserções: exatamente **1** sucesso, **7** recusas, **1** pagamento registrado e status
`CONFIRMADO`.

```bash
cd jfood-administrativo && ./mvnw test
```

> O teste precisa do PostgreSQL de pé. Rodá-lo contra um H2 em memória testaria outra coisa: o
> `SELECT ... FOR UPDATE` é uma característica do banco, não da JPA.
