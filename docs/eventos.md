# Eventos

Contratos dos tópicos do Kafka. Cada serviço declara as próprias classes para ler e escrever; o contrato é este documento, sem biblioteca compartilhada entre serviços.

## `payment.events`

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
