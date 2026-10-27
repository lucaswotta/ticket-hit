# 0006. Fluxo de pagamento no ticketing

Status: aceita

## Contexto

Pagar um hold atravessa três fronteiras: o banco do `ticketing`, uma chamada HTTP ao `payment` e um evento que volta pelo Kafka. Cada fronteira pode falhar sozinha. O comprador não pode perder o ingresso pago, ser cobrado duas vezes nem receber ingresso que já voltou ao estoque.

## Decisão

- `POST /api/holds/{id}/payment` muda o hold de `HELD` para `PAYING` só se o prazo não venceu. O job de expiração ignora `PAYING`: o `payment` sempre chega a uma decisão, e o outbox garante que ela é publicada (ADR 0004).
- `HoldService.pay` não abre transação. Cada passo grava sozinho e a falha desfaz o `PAYING` explicitamente. Segurar uma conexão do pool durante uma chamada HTTP com retry esgotaria o pool sob carga.
- `PaymentClient`: connect timeout de 1 s, read timeout de 2 s, até 3 retries com backoff exponencial a partir de 200 ms, só para falha de rede, timeout e 5xx. Todas as tentativas levam a mesma `Idempotency-Key` (ADR 0005). Esgotadas as tentativas, o hold volta a `HELD` e a API responde 503 `PAYMENT_UNAVAILABLE` com `Retry-After`.
- `PaymentEventListener` consome `payment-events`. `confirm` e `release` só mudam holds em `HELD` ou `PAYING`, então evento repetido não faz nada.
- A confirmação numera a compra numa instrução: soma a quantidade em `sale.sold_tickets` e grava o total em `hold.last_ticket_number`. O lock da linha da venda impede dois números iguais.
- Falha ao processar um evento: 5 tentativas com 1 s de intervalo e depois `payment-events-dlt`, com WARN no log. JSON inválido vai direto para o DLT.
- Os tópicos são criados por `infra/kafka/create-topics.sh` num container de execução única do Compose, e o broker não cria tópico sozinho. Nenhum serviço depende da ordem de subida do outro.

## Alternativas

- Transação em volta da chamada HTTP: segura conexão do banco durante segundos de retry.
- Deixar `PAYING` expirar: o pagamento aprovado chegaria para um ingresso que já voltou ao estoque.
- Saga com orquestrador dedicado: mais um serviço para um fluxo de dois passos.

## Consequências

- Se a resposta do `payment` se perder depois de 4 tentativas, o hold volta a `HELD` e a cobrança existe. Quando o evento de aprovação chega, a confirmação aceita `HELD`, e o comprador recebe o ingresso.
- Aprovação que chega para um hold já `EXPIRED` não emite ingresso e gera um WARN de estorno necessário. O estorno fica fora do escopo.
- Rodando os dois serviços contra o Compose, com o `payment` recusando 50% das chamadas: 12 POSTs (4 com 503, repetidos), 8 cobranças para 8 holds, 8 confirmações numeradas de 2 a 16.
- `HoldPaymentIT.concurrentConfirmationsGetDistinctNumbers` confirma 100 holds ao mesmo tempo e confere 100 números distintos.
