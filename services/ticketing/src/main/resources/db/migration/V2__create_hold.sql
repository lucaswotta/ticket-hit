create table hold (
    id uuid primary key default uuidv7(),
    sale_id uuid not null references sale (id),
    customer_id text not null,
    quantity integer not null check (quantity > 0),
    status text not null check (status in ('HELD', 'EXPIRED')),
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);

-- O job de expiração só procura holds ativos; holds encerrados ficam fora do índice.
create index hold_active_by_expiration on hold (expires_at) where status = 'HELD';
