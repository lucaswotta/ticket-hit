# 0004. Outbox transacional para eventos de negócio

Status: aceita

## Contexto

Quando o `payment` decide uma cobrança, o `ticketing` precisa saber para confirmar o ingresso ou devolvê-lo ao estoque. Gravar a decisão no PostgreSQL e publicar no Kafka são duas operações em dois sistemas. Se o serviço cair entre as duas, ou o banco diz que a cobrança foi aprovada e ninguém fica sabendo, ou o Kafka anuncia uma aprovação que o banco não tem.

## Decisão

- `ChargeProcessor.processDue` grava a decisão e o evento na tabela `outbox_event` na mesma transação. `Outbox.append` usa `Propagation.MANDATORY`: chamá-lo fora de uma transação é erro.
- `OutboxRelay.publishPending` lê os eventos não publicados com `for update skip locked`, envia cada um ao Kafka esperando a confirmação (`acks=all`) e marca `published_at`.
- A chave da mensagem é o id do hold. Eventos do mesmo hold caem na mesma partição e chegam na ordem.

## Alternativas

- Publicar no Kafka direto depois do commit: perde o evento se o serviço cair entre o commit e o envio.
- Transação distribuída (XA) entre PostgreSQL e Kafka: o Kafka não participa de XA, e o custo operacional não se paga.
- Change Data Capture com Debezium lendo o WAL: dispensa o relay, mas acrescenta um componente pesado à VM gratuita.

## Consequências

- A entrega é pelo menos uma vez. Se o Kafka confirmar e a transação do relay falhar antes do commit, o evento sai de novo. O consumidor precisa tolerar evento repetido; no `ticketing`, a confirmação só muda holds que ainda não foram confirmados.
- Um evento leva até `payment.outbox-interval` (200 ms) para sair, mais o envio.
- `OutboxRelayIT` publica no Kafka real, roda o relay duas vezes e confere uma única mensagem no tópico.
- `ChargeProcessorIT.concurrentProcessorsDecideEachChargeOnce` coloca 4 processadores sobre 100 cobranças. Sem o `for update skip locked`, uma cobrança saiu com 4 eventos.
