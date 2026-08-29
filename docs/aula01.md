# Etapa 1 — Modelagem conceitual e lógica

Gabarito da Etapa 1 do JFood, correspondente à Aula 1 (Fundamentos de Bancos de Dados Relacionais).

**Contexto.** No JFood, um **cliente** faz um **pedido** em um **restaurante**, escolhendo itens do
cardápio. O pedido é entregue por um **entregador** em um dos **endereços** do cliente, e é quitado
por um **pagamento**.

---

## 1. As entidades, classificadas

O critério é **dependência existencial**, o mesmo que faz `cartao_credito` depender de `assinatura`
no Javify: a entidade é fraca quando não consegue ser identificada sem o pai, e deixa de fazer
sentido no instante em que o pai some.

| Entidade | Forte ou fraca | Por quê |
|---|---|---|
| `usuario` | Forte | Existe por si: tem e-mail próprio, que é a identidade dele no sistema |
| `cliente`, `entregador`, `dono_restaurante` | Especializações | Não são fracas: são **o mesmo** usuário visto por um papel. A PK é a do usuário |
| `restaurante` | Forte | Tem CNPJ, nome e vida própria. Sobrevive ao dono trocar de conta |
| `item_cardapio` | Forte (dependente) | Tem identidade própria e é referenciado por pedidos antigos. Depende do restaurante por FK, mas não é identificado por ele |
| `pedido` | Forte | O número do pedido é a identidade que o cliente lê no app e o suporte usa |
| `item_pedido` | **Fraca** | "2 pizzas a R$ 44,90" não significa nada fora do pedido 137. Apagou o pedido, apagou o item |
| `endereco_entrega` | **Fraca** | "Casa" só existe em relação a um cliente. O apelido é único **dentro** do cliente, não no sistema |
| `pagamento` | **Fraca** | Não existe pagamento sem o pedido que ele quita |
| `avaliacao` | **Fraca** | Avalia-se **um pedido entregue**. Sem o pedido, não há o que avaliar |

> A diferença que mais confunde é entre `item_cardapio` e `item_pedido`. Os dois "pertencem" a um
> pai, mas só o segundo é fraco: um item de cardápio continua existindo — e continua sendo o nome
> que aparece no histórico de quem já pediu — mesmo que ninguém o peça nunca mais.

## 2. O atributo multivalorado: os endereços do cliente

Um cliente tem vários endereços de entrega, cada um com apelido e complemento.

**Por que `enderecos VARCHAR(500)` é um erro.** Ele viola a **1FN**, que exige valores atômicos.
As consequências não são teóricas:

- não dá para consultar por CEP sem `LIKE '%...%'`, que não usa índice (Aula 2);
- não dá para pôr `NOT NULL` no logradouro de um endereço só;
- alterar o complemento de um endereço vira reescrita da string inteira, com risco de corromper
  os outros;
- não dá para o `pedido` **referenciar** o endereço em que foi entregue — e essa referência é
  obrigatória, porque o cliente pode mudar de casa depois.

**A solução** é a tabela `endereco_entrega`, com FK para `cliente` e `UNIQUE (cliente_id, apelido)`:
"Casa" pode se repetir entre clientes, mas não dentro do mesmo cliente.

## 3. O N:N com atributos: pedido × itens do cardápio

Um pedido tem vários itens do cardápio; um item do cardápio aparece em vários pedidos. `quantidade`
e `preco_unitario` **não cabem em nenhuma das duas pontas**: não são propriedade do pedido (variam
por item) nem do item de cardápio (variam por pedido). Eles moram na tabela associativa
`item_pedido`, que é onde os dois se encontram.

**Por que o preço é copiado, e não lido do cardápio.** O cardápio muda: o restaurante reajusta a
pizza de R$ 44,90 para R$ 52,00. Se o histórico lesse o preço atual, o pedido de março passaria a
exibir o preço de setembro — e a soma dos itens deixaria de bater com o valor cobrado no cartão.

O `preco_unitario` do `item_pedido` não é redundância: é um **fato histórico**, o preço que valia
no instante da compra. Só parece duplicação de `item_cardapio.preco` porque, no dia do pedido, os
dois coincidem. O mesmo raciocínio vale para nota fiscal, folha de pagamento e extrato bancário.

> É a distinção entre **dado corrente** e **dado histórico**. A 3FN manda eliminar dependência
> transitiva de dado corrente; ela não manda jogar fora o passado.
