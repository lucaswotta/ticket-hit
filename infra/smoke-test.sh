#!/bin/sh
# Confere as garantias da infraestrutura com o Compose já de pé.
set -eu
cd "$(dirname "$0")/.."
. ./.env

fail() { echo "FALHOU: $1" >&2; exit 1; }

docker compose exec -T -e PGPASSWORD="$TICKETING_DB_PASSWORD" postgres \
    psql -h 127.0.0.1 -U ticketing -d ticketing -tAc 'select 1' > /dev/null \
    || fail "ticketing não conecta no próprio banco"

if docker compose exec -T -e PGPASSWORD="$TICKETING_DB_PASSWORD" postgres \
    psql -h 127.0.0.1 -U ticketing -d payment -tAc 'select 1' > /dev/null 2>&1; then
    fail "ticketing conecta no banco do payment"
fi

docker compose exec -T redis sh -c 'unset REDISCLI_AUTH; redis-cli ping' | grep -q NOAUTH \
    || fail "Redis aceita comando sem senha"

docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server localhost:9092 --describe --topic payment-events | grep -q "PartitionCount: 3" \
    || fail "tópico payment-events ausente ou sem 3 partições"

echo "infraestrutura ok"
