# 0003. SQL explícito no ticketing e JPA no payment

Status: aceita

## Contexto

As garantias do `ticketing` estão nas instruções SQL: o `UPDATE` condicional do estoque e a expiração com `SKIP LOCKED`. Um ORM esconde essas instruções atrás de métodos e de um contexto de persistência que decide quando gravar.

## Decisão

- O `ticketing` usa `JdbcClient` do Spring, com o SQL escrito no próprio repositório.
- O `payment` vai usar JPA com Spring Data, o que a maioria das vagas Java pede.

## Alternativas

- JPA nos dois serviços, com o `UPDATE` atômico em `@Query` nativa: funciona, mas mistura dois modelos no mesmo serviço e esconde o resto do acesso ao banco.

## Consequências

- Quem lê o `ticketing` vê exatamente o que vai ao banco, e o plano de cada consulta pode ser conferido com `explain`.
- O mapeamento de linha para objeto é manual quando o tipo não converte sozinho, como `timestamptz` para `Instant` em `HoldRepository`.
- O portfólio mostra os dois estilos, cada um onde faz mais sentido.
