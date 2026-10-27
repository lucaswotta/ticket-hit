alter table hold drop constraint hold_status_check;
alter table hold add constraint hold_status_check
    check (status in ('HELD', 'PAYING', 'CONFIRMED', 'EXPIRED', 'RELEASED'));

alter table hold
    add column payment_requested_at timestamptz,
    add column confirmed_at timestamptz,
    add column last_ticket_number integer;

alter table sale add column sold_tickets integer not null default 0;
alter table sale add constraint sold_tickets_within_total check (
    inventory_strategy = 'NAIVE' or sold_tickets between 0 and total_tickets
);
