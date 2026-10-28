# 0008. Gateway, identidade na borda e delegação para bots

Status: aceita

## Contexto

O comprador precisa vir de uma conta, para valer "um lugar por pessoa", e não do corpo da requisição. A demo tem até 100 bots por sala, cada um com identidade própria, e várias salas por hora. Um login no Keycloak por bot significaria um cálculo de hash de senha, caro de propósito, para cada bot de cada sala, numa VM gratuita.

## Decisão

- `gateway` com Spring Cloud Gateway reativo (release train 2025.1.4, compatível com o Boot 4.1). É a única porta pública; o `payment` não tem rota.
- Keycloak com o realm `ticket-hit` importado de `infra/keycloak/ticket-hit-realm.json`. Cliente `web` público com PKCE S256 e sem password grant; cliente `simulator` confidencial com client credentials e a role `simulator`. Os tokens levam a audiência `ticket-hit-api`, que o gateway exige.
- O gateway valida assinatura, emissor e audiência. O emissor é o endereço público do Keycloak e as chaves vêm do endereço interno (`jwk-set-uri`), que pode ser outro dentro do Docker.
- `CustomerIdentityFilter`, primeiro filtro do gateway: apaga `X-Customer-Id` e `X-Act-As` vindos do cliente e grava o comprador resolvido. Os serviços leem o comprador desse header.
- Delegação: com a role `simulator`, `X-Act-As: bot-<1 a 6 dígitos>` define o comprador. Sem a role, ou com outro formato de id, a resposta é 403 `ACT_AS_FORBIDDEN`.
- Recurso de outro comprador responde 404 (hold, pagamento, entrada da fila em `/entries/me`), para não confirmar que o id existe.
- Rate limit com o `RequestRateLimiter` do gateway sobre o Redis: 5/s com rajada de 10 por comprador nas rotas de compra, 50/s com rajada de 100 por IP nas públicas. Cada bot tem o próprio balde.
- Falha de conexão com um serviço vira 503 `UPSTREAM_UNAVAILABLE`.

## Alternativas

- Cada serviço validar o token de novo: defesa em profundidade, mas a mesma configuração de segurança em cada serviço, e a delegação dos bots repetida em todos.
- Uma conta no Keycloak por bot: identidade real, mas o custo de 1.000 logins por sala.
- Token emitido pelo próprio gateway para uso interno: dispensa confiar no header, ao custo de mais um par de chaves e de um emissor a manter.

## Consequências

- Os serviços confiam no `X-Customer-Id`. Se um serviço for exposto fora da rede interna, o header vira identidade forjável. No deploy, só o gateway publica porta.
- O IP usado no rate limit é o da conexão. Atrás de um proxy reverso, o gateway precisa ler `X-Forwarded-For` do proxy confiável; fica para a etapa de deploy.
- `sslRequired` do realm vem de `KEYCLOAK_SSL_REQUIRED` (padrão `external`). Num Docker com rede na faixa 100.64/10, que o Keycloak não reconhece como privada, o valor local precisa ser `none`.
- Testes: `GatewayIT` confere header forjado descartado, delegação permitida e recusada, role de abertura de venda, `payment` sem rota e rate limit por comprador; `UpstreamDownIT` confere o 503. Ponta a ponta com o Keycloak real e login PKCE: token adulterado 401, `X-Act-As` da usuária 403, hold de outro comprador 404, 429 depois da rajada de 10.
- O Spring Cloud Gateway 2025.1.4 registra `HV000271` (anotação `@Valid` em lista) nas próprias classes de propriedade. O aviso vem do framework e não do projeto.
