# 0007. Fila virtual, sorteio e passe de acesso

Status: aceita

## Contexto

Na abertura, a demanda é muito maior que o estoque. A fila decide a ordem de compra e quantos compram ao mesmo tempo. Ela precisa ser justa (conexão rápida e bot não furam), aguentar o pico de entrada e liberar compradores numa taxa fixa, com qualquer número de instâncias. O `ticketing` precisa saber que o comprador foi liberado sem consultar a fila a cada pedido.

## Decisão

- Estado no Redis, num sorted set por venda (`queue:{saleId}:waiting`). As chaves de uma venda levam o id entre chaves para cair no mesmo slot de um Redis Cluster.
- Sorteio por nota: quem entra antes da abertura recebe uma nota aleatória em [0, 1), gerada com `SecureRandom`; quem entra depois recebe `1 + milissegundos desde a abertura`. A ordem do sorted set já é o sorteio seguido da ordem de chegada. Antes da abertura, a API não devolve posição.
- `ZADD NX` em `join.lua`: entrar de novo não muda o lugar, e quem já foi liberado não volta para a fila.
- Liberação por token bucket em `admit.lua`: libera `floor((agora - última liberação) × taxa)` compradores e guarda o novo marco. O script é atômico; o job de 200 ms só define a granularidade. Fila vazia não acumula crédito.
- Passe: JWT ES256 assinado pela `queue`, com `iss`, `aud` = `ticketing`, `sub` = comprador, `sid` = venda, `exp` = liberação + 2 min e `jti` derivado de venda e comprador (UUID v3). O `kid` é o thumbprint da chave pública (RFC 7638).
- O `ticketing` tem só a chave pública, com algoritmo fixo em ES256, tolerância de relógio de 5 s, e confere venda e comprador. `hold_one_per_pass` (unique em `sale_id, pass_id`) faz um passe valer um hold.
- O par de chaves vem do `.env`, gerado por `infra/generate-env.sh`.

## Alternativas

- Sortear num job no instante da abertura: precisa disparar no momento exato e travar entradas durante o sorteio. A nota na entrada dá o mesmo resultado sem esse job.
- Liberar N por execução do job: duas instâncias liberariam o dobro, e atraso do job mudaria a taxa.
- Passe assinado com HMAC: o `ticketing` precisaria do segredo e poderia emitir passes.
- `ticketing` consultando a `queue` a cada hold: acopla a compra à disponibilidade da fila.
- Biblioteca JWT escrita no projeto: o parser é onde moram as falhas de `alg: none` e de troca de algoritmo.

## Consequências

- Quem deixa o hold expirar precisa voltar para a fila: o passe já foi usado.
- A taxa usa o relógio de quem chama o script; instâncias com relógios muito diferentes distorcem a liberação.
- Testes: 200 compradores sorteados saem fora da ordem de chegada; 8 instâncias chamando a liberação ao mesmo tempo liberam 30 em 3 s a 10/s (sem gravar o marco no Lua, liberaram 100); 20 holds simultâneos com o mesmo passe criam 1 (sem a constraint, criaram 2); `PassVerifierTest` recusa outra venda, outro comprador, passe vencido, outra chave, `alg: none`, HS256 com a chave pública, outro emissor e outra audiência.
- O `exp` do JWT tem precisão de segundo; o prazo devolvido pela API é truncado em segundos para coincidir com o do token.
