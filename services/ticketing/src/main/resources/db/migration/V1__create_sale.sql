create table sale (
    id uuid primary key default uuidv7(),
    name text not null,
    total_tickets integer not null check (total_tickets > 0),
    available_tickets integer not null,
    inventory_strategy text not null check (inventory_strategy in ('ATOMIC', 'NAIVE')),
    created_at timestamptz not null default now(),
    -- Segunda barreira contra overselling, no próprio banco. A estratégia
    -- ingênua fica de fora porque existe para mostrar o estoque negativo.
    constraint available_tickets_within_total check (
        inventory_strategy = 'NAIVE' or available_tickets between 0 and total_tickets
    )
);
