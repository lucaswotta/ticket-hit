# 0002. Expiração de holds com job e SKIP LOCKED

Status: aceita

## Contexto

Um hold segura ingressos por 10 minutos. Vencido o prazo sem pagamento, os ingressos precisam voltar ao estoque para a fila andar. Com mais de uma instância do `ticketing`, todas podem tentar expirar os mesmos holds ao mesmo tempo, e devolver o mesmo hold duas vezes cria ingresso do nada.

## Decisão

- `HoldExpirationJob` roda a cada `ticketing.hold.expiration-interval` (1 s) e chama `HoldService.expireDue`.
- `HoldRepository.expireDue` faz tudo numa instrução, com três CTEs: `due` seleciona até 500 holds vencidos com `for update skip locked`, `expired` marca esses holds como `EXPIRED` e `returned` soma as quantidades de volta em `sale`.
- `skip locked` faz cada instância pular os holds que outra já travou, em vez de esperar por eles. As instâncias dividem o trabalho sem bloquear umas às outras.
- O índice parcial `hold_active_by_expiration` cobre só holds com status `HELD`, que são os únicos que o job procura.

## Alternativas

- TTL no Redis com notificação de expiração: o Redis não garante a entrega da notificação, e um ingresso pode ficar preso para sempre.
- Expiração preguiçosa, conferindo o prazo só quando alguém consulta o hold: o estoque só volta quando alguém olha.

## Consequências

- Um ingresso volta ao estoque em até 1 s depois do vencimento, mais o tempo da instrução.
- `HoldServiceIT.concurrentExpirationReturnsEachHoldOnce` coloca 8 jobs para expirar 200 holds ao mesmo tempo e confere que o estoque volta exatamente ao total. Sem o `for update skip locked`, o teste falha: dois jobs devolvem o mesmo hold e a constraint `available_tickets_within_total` recusa o estoque acima do total.
- Com o job desligado (`ticketing.hold.expiration-enabled=false`), os testes controlam o relógio e decidem quando expirar.
