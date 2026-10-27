#!/bin/sh
set -eu

# Um banco e um dono por serviço. Sem o CONNECT de PUBLIC, cada usuário só
# entra no próprio banco, e um serviço não consegue ler as tabelas de outro.
create_database() {
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        -v name="$1" -v password="$2" <<'EOSQL'
CREATE ROLE :"name" LOGIN PASSWORD :'password';
CREATE DATABASE :"name" OWNER :"name";
REVOKE ALL ON DATABASE :"name" FROM PUBLIC;
EOSQL
}

create_database ticketing "$TICKETING_DB_PASSWORD"
create_database payment "$PAYMENT_DB_PASSWORD"
create_database keycloak "$KEYCLOAK_DB_PASSWORD"
