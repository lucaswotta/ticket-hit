# 0010. Raio-x por telemetria no Kafka e SSE

Status: aceita

## Contexto

A metade direita da tela mostra o backend trabalhando: compradores entrando na fila, holds, estoque caindo, ataques bloqueados. Os eventos precisam vir do que os serviços fizeram de fato, chegar ao navegador em tempo real e estar disponíveis para quem conecta depois do início e para o replay do fim da sala.

## Decisão

- `queue`, `ticketing` e `gateway` publicam em `telemetry-events` (contrato em `docs/eventos.md`), com a venda como chave. O `payment` não publica: ele não conhece a venda, e o efeito do pagamento aparece nos eventos do `ticketing`.
- Telemetria não usa outbox. O produtor de telemetria tem `max.block.ms` de 200 ms: com o Kafka fora, o evento se perde em vez de segurar a compra. Os metadados do tópico são buscados ao subir o serviço, porque o primeiro envio com a JVM fria passava dos 200 ms.
- No `ticketing`, os eventos saem depois do commit. `HOLD_CREATED` traz o estoque gravado (`returning available_tickets`), inclusive abaixo de zero.
- No `gateway`, a publicação roda fora do event loop.
- `xray`: consumer group por instância (cada instância precisa de todos os eventos), `auto-offset-reset: latest`, histórico de 2.000 eventos por venda em memória, stream SSE em `/api/xray/{saleId}/events` com histórico e depois ao vivo sob o mesmo monitor, heartbeat a cada 15 s, até 10 streams por venda (429 `TOO_MANY_STREAMS`). No gateway, a rota do `xray` não tem timeout de resposta.

## Alternativas

- Eventos de negócio (`payment-events`) como fonte do raio-x: cobrem só o pagamento.
- Outbox para telemetria: garante entrega, ao custo de uma escrita a mais no banco por evento, para algo que pode se perder.
- WebSocket: ver system design; o fluxo é só do servidor para o navegador.
- Histórico no Kafka, relendo o tópico a cada conexão: mais lento para conectar e mais carga no broker.

## Consequências

- Medido com os seis serviços, Keycloak e uma sala de 100 bots e 50 ingressos: o stream pelo gateway ficou aberto 40 s com heartbeat e trouxe 403 eventos (95 entradas na fila, 95 liberações, 50 holds, 48 recusas, 50 pedidos de pagamento, 50 confirmações, 15 bloqueios por rate limit), com o estoque informado indo até 0; na sala sem proteção, o estoque informado fica negativo.
- Um navegador lento segura o envio para os outros streams da mesma venda e o consumidor do Kafka, porque o envio acontece sob o monitor da venda. Com no máximo 10 streams por venda e uma sala por vez, o risco é aceito; uma fila por stream resolveria.
- A venda é a credencial do stream: o id é um UUID v7, com 74 bits aleatórios, e só o dono da sala o recebe. O stream mostra ids de bots e o `sub` opaco do visitante.
- Reiniciar o `xray` perde o histórico em memória; salas em andamento continuam ao vivo.
