# 0005. Idempotência na criação de cobranças

Status: aceita

## Contexto

O `ticketing` chama o `payment` com timeout e retry. Se a cobrança for criada e a resposta se perder, o retry chega com o mesmo pedido. Sem proteção, o comprador é cobrado duas vezes.

## Decisão

- `POST /api/charges` exige o header `Idempotency-Key`. O `ticketing` usa o id do hold como chave: um hold gera no máximo uma cobrança.
- `ChargeRepository.insertIfAbsent` faz `insert ... on conflict (idempotency_key) do nothing`. Com duas requisições simultâneas, a segunda espera a primeira no índice unique e não insere nada; as duas leem a mesma cobrança em seguida.
- A resposta repetida traz `Idempotent-Replayed: true`, com o mesmo corpo da primeira.
- A mesma chave com outro `holdId` é recusada com 422 `IDEMPOTENCY_KEY_REUSED`, em vez de devolver a cobrança de outro hold.

## Alternativas

- Inserir e capturar a violação da constraint unique: funciona, mas o caminho normal de uma corrida passa por exceção e enche o log de `duplicate key`.
- Guardar as chaves no Redis com TTL: a chave pode expirar ou se perder antes do retry, e a cobrança dupla volta.

## Consequências

- `ChargeServiceIT.concurrentRequestsWithSameKeyCreateOneCharge` envia 20 requisições simultâneas com a mesma chave e confere uma única cobrança.
- A criação usa SQL nativo dentro do repositório JPA, e o id vem do `uuidv7()` do banco.
