# Eventos

Contratos dos tópicos do Kafka. Cada serviço declara as próprias classes para ler e escrever; o contrato é este documento, sem biblioteca compartilhada entre serviços.

Os tópicos são criados por `infra/kafka/create-topics.sh`, antes de qualquer serviço subir. O broker não cria tópico automaticamente.

## `payment-events`

Produtor: `payment`, via outbox (ADR 0004). Consumidor: `ticketing`.

- Chave da mensagem: id do hold, em texto.
- Partições: 3.
- Entrega: pelo menos uma vez. O mesmo evento pode chegar mais de uma vez.

| Campo | Tipo | Descrição |
|---|---|---|
| `type` | texto | `PAYMENT_APPROVED` ou `PAYMENT_DECLINED` |
| `chargeId` | UUID | cobrança decidida |
| `holdId` | UUID | hold que originou a cobrança |
| `occurredAt` | instante ISO-8601 em UTC | momento da decisão, igual ao `decided_at` gravado |

Exemplo:

```json
{
  "type": "PAYMENT_APPROVED",
  "chargeId": "01a17f6f-fe67-7aa2-891a-da86bf02d426",
  "holdId": "2fca65b7-7b98-46b4-9226-7e1138c25815",
  "occurredAt": "2026-10-27T22:16:49.835442Z"
}
```

Campos novos podem ser acrescentados; consumidores ignoram campos que não conhecem. Remover ou mudar o tipo de um campo exige um tópico novo.

## `payment-events-dlt`

Mensagens de `payment-events` que o `ticketing` não conseguiu processar: JSON inválido vai direto; falha temporária vai depois de 5 tentativas com 1 s de intervalo. Cada mensagem cai na partição de mesmo número da origem, com os headers de exceção do Spring Kafka. Partições: 3.

## `telemetry-events`

Produtores: `queue`, `ticketing` e `gateway`, direto no Kafka, sem outbox. Consumidor: `xray`, um consumer group por instância.

- Chave da mensagem: id da venda, em texto. Eventos de uma venda chegam na ordem em que cada produtor os publicou.
- Partições: 3.
- Entrega: melhor esforço. Com o Kafka indisponível por mais de 200 ms, o evento se perde e o serviço registra no máximo um aviso a cada 30 s.
- Eventos do `ticketing` saem depois do commit da transação.

| Campo | Tipo | Descrição |
|---|---|---|
| `saleId` | UUID | venda, igual à chave |
| `service` | texto | `queue`, `ticketing` ou `gateway` |
| `type` | texto | ver a tabela abaixo |
| `at` | instante ISO-8601 em UTC | momento do evento no serviço |
| `customerId` | texto ou nulo | comprador (`sub` do token ou `bot-<n>`) |
| `data` | objeto | campos do tipo |

| `type` | Serviço | `data` |
|---|---|---|
| `QUEUE_JOINED` | `queue` | vazio |
| `QUEUE_ADMITTED` | `queue` | vazio |
| `HOLD_CREATED` | `ticketing` | `holdId`, `quantity`, `available` (estoque depois do hold; negativo no modo sem proteção) |
| `HOLD_REJECTED` | `ticketing` | `code`: `SOLD_OUT`, `INVALID_PASS`, `PASS_ALREADY_USED` ou `INVALID_QUANTITY` |
| `PAYMENT_REQUESTED` | `ticketing` | `holdId` |
| `PAYMENT_UNAVAILABLE` | `ticketing` | `holdId` |
| `HOLD_CONFIRMED` | `ticketing` | `holdId`, `ticket` (posição do último ingresso da compra) |
| `HOLD_RELEASED` | `ticketing` | `holdId`, `available` |
| `RATE_LIMITED` | `gateway` | `path` |
| `ACT_AS_FORBIDDEN` | `gateway` | vazio; `customerId` é o id pedido no `X-Act-As` |

