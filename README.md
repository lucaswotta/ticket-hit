# ticket-hit

Venda relâmpago de ingressos com fila virtual. Milhares de pessoas disputam poucos ingressos que abrem num horário marcado, e o sistema decide quem entra, em que ordem e por quanto tempo, sem vender um ingresso a mais e sem deixar ninguém furar a fila.

A demonstração coloca o visitante numa sala com cerca de 100 bots que usam a mesma API que ele, e mostra ao lado, em tempo real, o que cada serviço do backend está fazendo (o "raio-x").

## O que o projeto resolve

- **Estoque sob concorrência:** a retirada é um `UPDATE` condicional no PostgreSQL. Num teste com 1.000 compradores contra 500 ingressos, a estratégia atômica vende exatamente 500; a ingênua (ler, conferir, gravar) vende entre 557 e 645. As duas existem para a demo mostrar a diferença. [ADR 0001](docs/adr/0001-estrategia-de-estoque.md)
- **Fila justa:** quem chega antes da abertura entra num sorteio; depois, por ordem de chegada. A liberação emite um passe assinado (ES256) com prazo de 2 minutos, conferido pelo `ticketing` sem consultar a fila. [ADR 0007](docs/adr/0007-fila-virtual-e-passe.md)
- **Pagamento que falha de propósito:** serviço falso que atrasa e erra, chamado com timeout, retry e chave de idempotência para não cobrar duas vezes. [ADR 0005](docs/adr/0005-idempotencia-do-pagamento.md)
- **Eventos sem inconsistência:** outbox transacional entre o banco e o Kafka. [ADR 0004](docs/adr/0004-outbox-transacional.md)
- **Bots abusivos:** parte dos bots tenta forjar passe, reaproveitar o de outro comprador ou estourar o rate limit, e as tentativas aparecem bloqueadas. [ADR 0009](docs/adr/0009-simulador-de-bots.md)

## Arquitetura

| Serviço | Porta | Responsabilidade | Estado |
|---|---|---|---|
| `gateway` | 8080 | porta única: roteamento, validação do JWT, identidade do comprador, rate limit | Redis |
| `queue` | 8081 | sala de espera, sorteio, posição, liberação, emissão do passe | Redis |
| `ticketing` | 8082 | estoque, hold, expiração, compra | PostgreSQL |
| `payment` | 8083 | pagamento falso, confirmado por evento | PostgreSQL |
| `xray` | 8084 | consome a telemetria e entrega ao navegador por SSE | nenhum |
| `simulator` | 8085 | cria a sala da demo e conduz os bots pelo `gateway` | memória |
| Keycloak | 8180 | cadastro, login e tokens OIDC | PostgreSQL |

O desenho completo, com fluxos, requisitos e decisões, está em [docs/system-design.md](docs/system-design.md). O contrato dos eventos está em [docs/eventos.md](docs/eventos.md).

## Stack

Java 25, Spring Boot 4.1, Maven, PostgreSQL 18, Redis 8, Kafka 4 (KRaft), Keycloak 26, Testcontainers e Docker Compose.

## Rodando localmente

Pré-requisitos: JDK 25, Docker e `openssl`.

```sh
# senhas aleatórias e par de chaves do passe
infra/generate-env.sh

# PostgreSQL, Redis, Kafka e Keycloak
docker compose up -d --wait

# confere isolamento dos bancos, senha do Redis e tópicos
infra/smoke-test.sh

# build e testes de todos os serviços
./mvnw verify
```

Os testes de integração sobem a própria infraestrutura com Testcontainers, então `./mvnw verify` não depende do Compose.

## Status

Backend em construção; o site do festival (`web`) e o deploy no Oracle Cloud Always Free ainda não começaram. Os pontos em aberto estão no fim do [system design](docs/system-design.md#em-aberto).

O festival, os artistas e os compradores são fictícios, e nenhum pagamento real é feito.
