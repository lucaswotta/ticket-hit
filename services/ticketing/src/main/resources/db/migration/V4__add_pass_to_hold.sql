-- Holds anteriores ao passe recebem ids aleatórios para a coluna nascer não nula.
alter table hold add column pass_id uuid not null default uuidv7();
alter table hold alter column pass_id drop default;

-- Um passe vale um hold: é a regra "um lugar por pessoa" e barra o reuso de
-- passe. O HoldService confere antes; a constraint segura a corrida.
alter table hold add constraint hold_one_per_pass unique (sale_id, pass_id);
