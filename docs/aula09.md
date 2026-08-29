# Etapa 9 — Cache no horário do almoço

Gabarito da Etapa 9 do JFood, correspondente à Aula 9. O cache entra no mesmo
[`jfood-catalogo`](../jfood-catalogo) da Etapa 8.

Delivery tem um perfil de carga brutalmente concentrado: das 11h30 às 13h e das 19h às 21h. Fora
disso, o sistema dorme.

## Como reproduzir

```bash
docker compose up -d
cd jfood-catalogo && ./mvnw spring-boot:run

docker exec jfood-redis redis-cli KEYS '*'          # ver as chaves
docker exec jfood-redis redis-cli GET 'restaurante-detalhe::<id>'
```

---

## 1. A conta que justifica o cache

Com os números da Etapa 7 e uma premissa a mais — o cliente ativo abre a tela inicial cerca de
**4 vezes** por sessão, entre voltar da busca e comparar restaurantes:

```
clientes ativos no pico            = 3.600.000
aberturas da tela inicial          = 3.600.000 × 4        = 14.400.000
janela de pico                     = 2 h                  =      7.200 s
requisicoes/s na tela inicial      = 14.400.000 / 7.200   =      2.000 req/s
```

A 60 ms por consulta ao MongoDB:

```
tempo de consulta por segundo      = 2.000 × 0,060 s      =        120 s/s
nucleos necessarios SEM cache      =                               120
```

**120 núcleos** dedicados a devolver, duas mil vezes por segundo, **a mesma resposta** — um catálogo
que muda algumas vezes por semana. Com hit rate de 95% caem para **6**; com 99%, para **1,2**.

Repare na assimetria que justifica tudo: a tela inicial é lida 2.000 vezes por segundo e escrita
algumas vezes por semana. É o caso didático de cache — e por isso o Redis entra **depois** da conta,
e não antes.

## 2. Cache Aside no cardápio

```java
@Cacheable(cacheNames = "restaurante-detalhe", key = "#id")
public Restaurante detalhar(String id) {
    log.info("MISS -> consultando o MongoDB: detalhe do restaurante {}", id);
    ...
}
```

**A instrumentação primeiro.** A linha de log só aparece no MISS: se ela some da segunda chamada em
diante, o cache está funcionando. É o jeito mais barato de enxergar o efeito — e sem ele o
experimento inteiro vira fé.

```
duas chamadas ao mesmo detalhe -> MISS no log: 1
MISS e HIT devolvem o mesmo JSON (conferido com diff)
```

**A chave é explícita, por SpEL.** Deixar o `toString()` do `Pageable` virar chave produz coisas como
`Page request [number: 0, size: 20, sort: nota_media: DESC]`: funciona, é ilegível no `redis-cli`,
muda de formato entre versões do Spring Data e vaza detalhe interno do framework para dentro do
banco. Com `key = "#pageable.pageNumber + '-' + #pageable.pageSize"`:

```
restaurante-detalhe::6a9303dda6d8524125d1a7bd   ttl=3600
restaurantes-listagem::0-3                      ttl=3600
```

## 3. Serialização JSON

`RedisCacheConfiguration` com `GenericJacksonJsonRedisSerializer`, `PolymorphicTypeValidator`
restrito ao pacote de domínio do JFood, `NON_FINAL_AND_RECORDS`, datas em ISO-8601, TTL global de 60
minutos e `disableCachingNullValues()`.

O valor no `redis-cli` é **texto legível**:

```
["br.com.unipds.jfood.catalogo.domain.Restaurante",
 {"id":"6a93...","nome":"Sushi Yuki","categoria":"Japonesa",
  "notaMedia":["java.math.BigDecimal",4.9],"abreAs":"19:00:00", ...}]
```

O `["classe", {...}]` é o *default typing* — sem ele, o Jackson não sabe se aquele objeto é um
`Restaurante` ou um mapa qualquer na hora de ler de volta.

**Por que o validador é obrigatório.** Confiar cegamente num nome de classe vindo de texto externo é
uma das maiores falhas do Java moderno: historicamente, atacantes enviavam JSON com o nome de uma
classe interna que executava comandos no servidor — *Remote Code Execution*. Até o Jackson 2.9
bastava `enableDefaultTyping()`; a partir do 2.10 esse método foi removido, e a única forma
não-*deprecated* **exige** um `PolymorphicTypeValidator`. Sem ele, o código não compila.

**`NON_FINAL_AND_RECORDS`, e não `NON_FINAL`.** Classes `final` não têm subclasses, então não há
polimorfismo a resolver e o carimbo seria desperdício. Só que **records também são final** — e as
entidades do JFood são todas records. Sem o `_AND_RECORDS`, elas perderiam o tipo e não voltariam do
cache.

### Três armadilhas que este gabarito encontrou

**(a) O `ObjectMapper` do cache não pode ser um `@Bean`.** O Spring Boot já registra um
`ObjectMapper` `@Primary` para as respostas HTTP. Publicar um segundo faz o `@Primary` vencer a
resolução por nome do parâmetro — e o cache recebe o mapper do web, **sem** default typing. O
sintoma é cruel: a gravação funciona, o JSON no Redis parece perfeito, e a leitura estoura com

```
ClassCastException: class java.util.LinkedHashMap cannot be cast to
class br.com.unipds.jfood.catalogo.domain.Restaurante
```

— **só no HIT, nunca no MISS**. Ou seja: passa no primeiro teste que qualquer um escreveria. A
solução é construir o mapper em um método privado, usado pelos dois beans.

**(b) O `PageImplMixIn` da apostila não resolve no Jackson 3.** `PageImpl` não tem construtor que o
Jackson consiga usar:

```
Cannot construct instance of `org.springframework.data.domain.PageImpl`
(no Creators, like default constructor, exist)
```

E um mixin só é aplicado quando a assinatura do construtor **casa** com um construtor real da classe
alvo — os de `PageImpl` recebem um `Pageable`, que por sua vez também não é desserializável. Em vez
de empilhar remendos, o cache guarda um record próprio,
[`PaginaRestaurantes`](../jfood-catalogo/src/main/java/br/com/unipds/jfood/catalogo/domain/PaginaRestaurantes.java),
e o controller o converte para `Page` na saída.

E a solução é melhor do que o remendo pelo mesmo motivo do `VIA_DTO`: **não acoplar o conteúdo do
cache a uma classe interna do Spring.** Um `PageImpl` cacheado hoje é uma incompatibilidade binária
esperando o próximo upgrade — com a agravante de que os dados já estão gravados no Redis.

**(c) Self-invocation, de novo.** O método `@Cacheable` não pode ser envolvido por outro método do
**mesmo** bean que faça a conversão para `Page`: a chamada interna passa por `this`, não pelo proxy,
e a anotação é ignorada — a mesma armadilha da Etapa 5, agora com cache em vez de transação. Quem
converte é o controller.

## 4. Invalidação correta

```java
@Caching(evict = {
        @CacheEvict(cacheNames = "restaurante-detalhe", key = "#id"),
        @CacheEvict(cacheNames = "restaurantes-listagem", allEntries = true),
        @CacheEvict(cacheNames = "restaurantes-destaque", allEntries = true)
})
public Restaurante atualizar(String id, Restaurante restaurante) { ... }
```

O ciclo completo, medido:

```
chaves antes:  restaurantes-listagem::0-3  restaurantes-listagem::0-1  restaurante-detalhe::6a93...
PUT (nota 4.9 -> 5.0) -> 200
chaves depois: []
detalhe relido:  nota 5.0
listagem relida: Sushi Yuki 5.0
MISS no log: 2   (os dois caches voltaram ao banco)
```

**Esquecer o evict da listagem é o erro clássico**: o detalhe volta certo e a listagem continua
mostrando o dado antigo, até o TTL de 60 minutos expirar. O suporte recebe "atualizei e não mudou",
o desenvolvedor testa a tela de detalhe, vê o valor novo e fecha o chamado.

**`allEntries = true` é a resposta honesta.** Não dá para saber em quais páginas aquele restaurante
aparecia — a ordenação é por nota, e mudar a nota muda a página. E **`@CachePut` não serve aqui**:
ele grava o retorno do método em **uma** chave, e o problema são N chaves de listagem que ficaram
erradas de uma vez.

> **O TTL global de 60 min é a rede de segurança.** Mesmo que a invalidação falhe, a inconsistência
> tem prazo de validade. Ele não substitui o evict — apenas limita o estrago.

## 5. Write Behind no contador de visualizações

Gravar `visualizacoes` no MongoDB a cada abertura de tela seria um `UPDATE` por request na tela mais
acessada do app — e o cache do detalhe deixaria de servir para alguma coisa, porque a **escrita**
continuaria indo ao banco de qualquer jeito.

O Redis absorve os incrementos (`INCR` é atômico e custa microssegundos) e um `@Scheduled` de 5
minutos consolida com **um** update por restaurante, em um `bulkOps` de um round-trip só.

```
visualizacoes no MongoDB antes: 0
50 POSTs simultâneos
contador no Redis:              50
visualizacoes no MongoDB agora: 0     <- ainda não foi
POST /experimentos/write-behind/consolidar -> {"consolidadas":50}
visualizacoes no MongoDB depois: 50
chave no Redis depois:          []
```

O flush usa `GETDEL`, que lê e apaga em uma operação atômica — sem isso, um incremento que chegasse
entre o `GET` e o `DEL` se perderia em silêncio.

### O que se perde se o Redis morrer entre dois flushes

Até **5 minutos** de incrementos daquele intervalo. Não há WAL, não há confirmação, não há como
reconstruir: os contadores simplesmente voltam de onde estavam no último flush.

**Por que isso é aceitável aqui.** `visualizacoes` é uma métrica de produto, usada para ordenar
"populares" e alimentar relatório. Um erro de alguns milhares em um número que já está na casa dos
milhões não muda nenhuma decisão, não aparece em nenhuma tela como valor exato e ninguém consegue
apontar que ele está errado. O contador é **aproximado por natureza** — e é honesto que seja.

**Por que não seria no `valor_total` de um pedido.** Ali o número é **o valor cobrado do cliente**.
Perder 5 minutos de escritas significa cobrar o valor errado, ou não cobrar; significa nota fiscal
que não bate, repasse ao restaurante que não fecha e cliente que liga para o suporte. Não existe
"aproximadamente R$ 89,80". Por isso `valor_total` vive em uma transação ACID no PostgreSQL, e o
contador de visualizações vive em um `INCR` no Redis.

**A regra:** write-behind troca durabilidade por throughput. Faça a troca onde a perda é
estatística; nunca onde ela é contábil.

## 6. O cache stampede

**O experimento roda dentro da JVM**, e não com `xargs -P 200 curl`. A razão está no primeiro
resultado que obtive por fora:

```
TTL fixo   : 25 acessos ao MongoDB para 200 requisicoes
com jitter : 2  acessos ao MongoDB para 200 requisicoes
```

Números bonitos e **sem significado**: os 200 processos `curl` não partem juntos — o primeiro já
gravou o cache antes de o quinquagésimo ter subido —, e a diferença mediu a velocidade de criação de
processo, não a estratégia de cache. Com uma `CountDownLatch`, as N threads chegam ao cache no
**mesmo instante**:

| Estratégia | Rodada 1 | Rodada 2 | Rodada 3 |
|---|---|---|---|
| TTL fixo | **200**/200 | 198/200 | **200**/200 |
| TTL + jitter | **200**/200 | **200**/200 | **200**/200 |
| TTL + trava de recálculo | **1**/200 | **1**/200 | **1**/200 |

### O resultado que contraria a intuição: jitter não resolve isto

Com **uma** chave e 200 requisições no instante da expiração, o jitter não muda nada — e não poderia.
Ele altera **quando** a chave expira, não **quantas** requisições encontram o miss depois que ela
expirou. Todas as 200 continuam chegando ao banco.

**O que o jitter realmente faz** é dessincronizar prazos entre chaves:

```
TTL fixo (5 gravações):        60s  60s  60s  60s  60s
TTL com jitter (5 gravações):  62s  63s  72s  65s  60s
```

E é para isso que ele serve: quando **muitas** chaves são aquecidas juntas — um deploy, um restart do
Redis, um flush —, todas expiram no mesmo segundo e o banco leva N ondas ao mesmo tempo. O jitter
espalha essas ondas. É proteção contra **expiração em massa**, não contra herd em uma chave.

### O que resolve: trava de recálculo (*single flight*)

No miss, cada requisição tenta um `SET NX` de uma chave de trava. Só a primeira consegue e vai ao
banco; as outras esperam alguns milissegundos e releem o cache.

```
COM_TRAVA: 1 acesso ao MongoDB para 200 requisicoes, em 47-62 ms
```

**De 200 para 1.** A trava tem TTL próprio de 5 s: se o processo que a pegou morrer no meio, ela
expira sozinha em vez de bloquear o cache para sempre. E quem perde a corrida tem um limite de espera
— passado ele, vai ao banco mesmo. Melhor uma consulta a mais do que uma requisição sem resposta.

**As duas técnicas são complementares**, e a apostila trata as duas: jitter contra expiração em
massa, trava contra herd na chave quente.

## 7. Eviction

`maxmemory 3mb` e `allkeys-lru`, com o cache aquecido e depois pressionado por escritas de 20 KB —
**com o cliente continuando a abrir a tela do restaurante mais popular** enquanto a pressão sobe:

```
uso: 2.98M | evicted_keys: 960
QUENTE (relido a cada lote) sobreviveu? 1
FRIO   (lido uma vez)       sobreviveu? 0
detalhes restantes: 1 de 7
chaves de lixo restantes: 64 de 300
```

O LRU fez exatamente o que promete: manteve o que estava sendo usado e descartou o resto — inclusive
parte do próprio lixo, que também envelheceu.

> **Um detalhe do experimento que ensina o algoritmo.** Na primeira tentativa, escrevendo todo o lixo
> **antes** de reler o cache, o restaurante popular também foi despejado. E está certo: LRU ordena
> por **último acesso**, e todas as entradas do cache tinham sido acessadas antes de qualquer
> escrita de lixo. Não existe "chave importante" para o LRU — existe chave acessada há mais ou menos
> tempo. Simular pressão sem simular tráfego mede outra coisa.

### LRU ou LFU para o catálogo do JFood?

**LRU**, e o motivo é o perfil de acesso do delivery.

O que o LFU faria melhor: proteger itens com alta frequência **histórica** de acesso. O que ele faz
pior: demorar a se adaptar a mudanças de padrão, porque um contador alto acumulado leva tempo para
decair.

E o padrão do JFood **muda o dia inteiro**:

- às 12h os restaurantes de almoço executivo são os mais acessados; às 20h, as pizzarias;
- um restaurante novo que entra em campanha vira o mais acessado da região em uma tarde — e com LFU
  ele competiria com contadores acumulados durante semanas;
- o catálogo tem sazonalidade de dia da semana, de feriado e de clima (chove, todo mundo pede).

O LRU responde a "quem está sendo acessado **agora**", que é exatamente a pergunta certa quando o
conjunto quente troca a cada poucas horas. O LFU seria a escolha certa para um conjunto quente
estável — um catálogo de produtos com curva de popularidade fixa, por exemplo.

> **Nota de operação:** `allkeys-lru` despeja qualquer chave, inclusive as sem TTL. Como aqui o Redis
> é **só** cache — nada nele é fonte da verdade —, essa é a política certa. Se o mesmo Redis também
> guardasse sessões ou filas, `volatile-lru` seria mais seguro: despeja só o que tem TTL.

---

## Critério de pronto

No pico simulado, o número de consultas ao MongoDB cai pelo menos uma ordem de grandeza:

| Cenário | Consultas ao MongoDB |
|---|---|
| 200 requisições simultâneas, sem cache | 200 |
| Com cache aquecido | 0 |
| Com cache expirado e trava de recálculo | **1** |

E para cada hit e cada miss há uma linha no log dizendo qual anotação o produziu (`MISS -> ...` em
`CatalogoService.detalhar`, `CatalogoService.listar` e `DestaqueService.buscarNoBanco`).
