create table charge (
    id uuid primary key default uuidv7(),
    idempotency_key text not null unique,
    hold_id uuid not null,
    customer_id text not null,
    status text not null check (status in ('PENDING', 'APPROVED', 'DECLINED')),
    created_at timestamptz not null,
    decided_at timestamptz,
    check ((status = 'PENDING') = (decided_at is null))
);

create index charge_pending_by_creation on charge (created_at) where status = 'PENDING';

-- Eventos gravados na mesma transação da mudança que os gerou; o relay
-- publica no Kafka depois. Ver docs/adr/0004-outbox-transacional.md.
create table outbox_event (
    id uuid primary key,
    topic text not null,
    message_key text not null,
    payload jsonb not null,
    created_at timestamptz not null,
    published_at timestamptz
);

create index outbox_event_unpublished on outbox_event (created_at) where published_at is null;
