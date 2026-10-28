# 0009. Simulador de bots

Status: aceita

## Contexto

A demo precisa de cerca de 1.000 compradores disputando o estoque com o visitante, além de bots abusivos que o sistema bloqueia. Os números da sala (vendidos, recusados, ataques bloqueados) só valem como prova se os bots passarem pelo mesmo caminho que um comprador real.

## Decisão

- O `simulator` cria a sala (venda e fila) e solta os bots, uma thread virtual por bot. Tudo pelo `gateway`, com o token do `simulator` por client credentials e o bot no `X-Act-As` (ADR 0008).
- Comprador comum (`BuyerBot`): entra na fila durante o lobby, consulta a posição a cada metade do tempo estimado (entre 0,5 s e 5 s), espera um tempo de reação de 200 a 1.500 ms, segura 1 ingresso, paga e acompanha o hold. 429 faz esperar 1 s e tentar de novo; 503 no pagamento faz tentar de novo; 504 faz consultar o hold antes de pagar outra vez.
- Bots abusivos, 5% da sala, em quatro ataques: passe forjado, passe de outro bot, hold sem passe e rajada de 30 consultas em paralelo. Cada um tem uma resposta esperada (403, 403, 400, 429). Sem ela, o resultado é `ATTACK_SUCCEEDED`.
- Modo sem proteção: estoque ingênuo e fila liberando 1.000 por segundo. Com a fila no ritmo normal, ela sozinha protege o estoque e a falha não aparece.
- Até 3 salas ao mesmo tempo; acima disso, 503 `ROOMS_FULL` com `Retry-After: 30`. Salas ficam em memória.
- O token do `simulator` é renovado por uma chamada de cada vez (`SingleFlightAuthorizedClientManager`).

## Alternativas

- Bots chamando os serviços direto, sem gateway: mais leve, mas os números não provariam autenticação, rate limit nem delegação.
- Uma conta no Keycloak por bot: ver ADR 0008.
- Salas persistidas: a sala dura menos de um minuto; perder as salas num restart é aceitável.

## Consequências

- Medido num notebook de 8 threads com outros programas abertos, serviços aquecidos e recusa de pagamento desligada: sala protegida com 500 vendidos de 500 e estoque 0; sala sem proteção com 507 vendidos e estoque -7; nas duas, nenhum erro e nenhum ataque bem-sucedido.
- Com recusa de pagamento ligada, os ingressos devolvidos compensam a venda a mais e o estado final fecha em zero; o estoque negativo aparece durante a venda.
- No pico, gateway e simulator usam cerca de 3 e 2,5 núcleos. Com a máquina disputada, as respostas passam de 10 s e os bots terminam em erro de I/O. A VM gratuita tem 4 núcleos: o número de bots por sala precisa ser medido lá.
- A primeira sala depois de subir os serviços roda com a JVM fria e erra mais; o deploy precisa de uma sala de aquecimento antes de abrir a demo.
- Problemas que as salas reais revelaram e foram corrigidos: balde de rate limit único para todos os bots (o gateway resolvia o comprador depois de apagar o `X-Act-As`), corrida em `QueueService.entry` durante a liberação, renovação simultânea do token, orçamento de retry do `ticketing` maior que o timeout do gateway, consultas dos bots à rota pública dividindo o balde por IP.
