# 0001. Estratégia de estoque do ticketing

Status: aceita

## Contexto

Na abertura da venda, centenas de requisições tentam tirar ingresso do mesmo show ao mesmo tempo. O padrão ler, conferir na aplicação e gravar deixa duas requisições lerem o mesmo saldo e as duas venderem. O modo sem proteção da demo precisa mostrar exatamente essa falha, ao lado da versão correta.

## Decisão

- A retirada de estoque fica atrás da interface `TicketInventory`, com duas implementações. Cada venda guarda em `sale.inventory_strategy` qual delas usa.
- `AtomicTicketInventory` faz um `UPDATE` condicional (`where available_tickets >= :quantity`). O PostgreSQL confere e subtrai na mesma instrução, com a linha travada.
- `NaiveTicketInventory` faz `SELECT`, confere em Java, espera `ticketing.naive.read-write-gap` (20 ms) e faz `UPDATE` relativo. Existe só para a demo. A espera ocupa o lugar do trabalho que código real faz entre ler e gravar; sem ela, a fila espalha os compradores o bastante para a janela de microssegundos quase nunca ser atingida.
- A constraint `available_tickets_within_total` impede estoque negativo ou acima do total em toda venda que não seja `NAIVE`. É a segunda barreira, caso algum código futuro escape da primeira.

## Alternativas

- `SELECT ... FOR UPDATE` (lock pessimista): correto, mas são duas idas ao banco segurando o lock entre elas.
- Coluna de versão (lock otimista): correto, mas sob disputa alta a maioria das tentativas falha e precisa repetir.
- `synchronized` ou lock em memória: só protege uma instância da aplicação.

## Consequências

- `TicketInventoryIT` solta 1.000 compradores contra 500 ingressos num PostgreSQL real. A estratégia atômica vende exatamente 500. A ingênua vende mais de 500 e termina com estoque negativo; em 40 execuções seguidas, vendeu entre 557 e 645.
- Todas as compras de um show disputam a mesma linha. Na escala da demo, com a fila limitando quem compra, isso basta. Se a disputa virar gargalo medido, o caminho é dividir o estoque em várias linhas.
- O banco aceita estoque negativo nas vendas `NAIVE`. Esse modo nunca pode ser o padrão de uma venda.
